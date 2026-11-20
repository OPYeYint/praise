import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import {
  ApiError,
  createGratitude,
  deleteGratitude,
  listGratitudes,
  randomGratitude,
  updateGratitude,
  type FieldError,
  type Gratitude,
  type GratitudeInput,
} from './api.ts'
import { AuthProvider, useAuth } from './auth.tsx'

const MOODS = ['calm', 'grateful', 'bright', 'tender', 'proud'] as const

type Panel = 'write' | 'journal'

type Draft = {
  id: number | null
  title: string
  content: string
  moodTag: string
  entryDate: string
}

const emptyDraft = (): Draft => ({
  id: null,
  title: '',
  content: '',
  moodTag: '',
  entryDate: '',
})

export default function App() {
  return (
    <AuthProvider>
      <Gate />
    </AuthProvider>
  )
}

function Gate() {
  const { user, ready } = useAuth()
  if (!ready) {
    return (
      <main className="gate">
        <p className="kicker">Praise</p>
        <p>Opening your journal…</p>
      </main>
    )
  }
  if (!user) {
    return <AuthScreen />
  }
  return <JournalApp />
}

function AuthScreen() {
  const { signIn, signUp } = useAuth()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>([])
  const [busy, setBusy] = useState(false)

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setFieldErrors([])
    setBusy(true)
    const name = username.trim().toLowerCase()
    try {
      if (mode === 'login') {
        await signIn(name, password)
      } else {
        await signUp(name, password)
      }
    } catch (caught) {
      const parsed = readError(caught)
      setError(parsed.message)
      setFieldErrors(parsed.errors)
    } finally {
      setBusy(false)
    }
  }

  const creating = mode === 'register'

  return (
    <main className="gate">
      <section className="auth-card">
        <p className="kicker">Private journal</p>
        <h1 className="brand">Praise</h1>
        <p className="lede">A quiet place for what you are glad of.</p>
        <form className="form" onSubmit={onSubmit}>
          <label className="field">
            <span>Username</span>
            <input
              name="username"
              autoComplete="username"
              minLength={3}
              maxLength={50}
              pattern="[A-Za-z0-9_]+"
              required
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
            <small>Letters, numbers, and underscores.</small>
            <FieldMessage errors={fieldErrors} field="username" />
          </label>
          <label className="field">
            <span>Password</span>
            <input
              name="password"
              type="password"
              autoComplete={creating ? 'new-password' : 'current-password'}
              minLength={creating ? 8 : 1}
              maxLength={72}
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            {creating ? <small>8 to 72 characters.</small> : null}
            <FieldMessage errors={fieldErrors} field="password" />
          </label>
          {error ? (
            <p className="form-error" role="alert">
              {error}
            </p>
          ) : null}
          <button className="button" type="submit" disabled={busy}>
            {busy ? 'One moment…' : creating ? 'Create account' : 'Sign in'}
          </button>
        </form>
        <button
          className="text-button"
          type="button"
          onClick={() => {
            setMode(creating ? 'login' : 'register')
            setError(null)
            setFieldErrors([])
          }}
        >
          {creating ? 'I already have an account' : 'Create an account'}
        </button>
      </section>
    </main>
  )
}

function JournalApp() {
  const { user, signOut } = useAuth()
  const [entries, setEntries] = useState<Gratitude[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [slots, setSlots] = useState<(Gratitude | null)[]>([null, null, null])
  const slotCursor = useRef(0)
  const [stageError, setStageError] = useState<string | null>(null)
  const [panel, setPanel] = useState<Panel | null>(null)
  const [draft, setDraft] = useState<Draft>(emptyDraft)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>([])
  const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null)

  const failAuth = useCallback(
    (caught: unknown) => {
      if (caught instanceof ApiError && caught.status === 401) {
        signOut()
        return true
      }
      return false
    },
    [signOut],
  )

  const refresh = useCallback(async () => {
    const rows = await listGratitudes()
    setEntries(rows)
    return rows
  }, [])

  useEffect(() => {
    let cancelled = false
    listGratitudes()
      .then((rows) => {
        if (!cancelled) {
          setEntries(rows)
          setSlots(filledSlots(rows))
        }
      })
      .catch((caught: unknown) => {
        if (cancelled || failAuth(caught)) {
          return
        }
        setLoadError(readError(caught).message)
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [failAuth])

  const spawn = useCallback((entry: Gratitude) => {
    setSlots((current) => {
      const next = [...current]
      const existing = next.findIndex((item) => item?.id === entry.id)
      if (existing >= 0) {
        next[existing] = entry
        return next
      }
      const empty = next.findIndex((item) => item === null)
      const index = empty >= 0 ? empty : slotCursor.current % 3
      if (empty < 0) {
        slotCursor.current = (slotCursor.current + 1) % 3
      }
      next[index] = entry
      return next
    })
  }, [])

  const summon = useCallback(
    async (manual: boolean) => {
      try {
        const entry = await randomGratitude()
        if (!entry) {
          setEntries([])
          setSlots([null, null, null])
          if (manual) {
            setStageError(null)
          }
          return
        }
        spawn(entry)
        setStageError(null)
      } catch (caught) {
        if (failAuth(caught)) {
          return
        }
        if (manual) {
          setStageError(readError(caught).message)
        }
      }
    },
    [failAuth, spawn],
  )

  useEffect(() => {
    if (entries.length === 0) {
      return
    }
    let timer = 0
    const tick = () => {
      void summon(false)
      timer = window.setTimeout(tick, randomBetween(9000, 16000))
    }
    timer = window.setTimeout(tick, 2400)
    return () => window.clearTimeout(timer)
  }, [entries.length, summon])

  function beginEdit(entry: Gratitude) {
    setDraft({
      id: entry.id,
      title: entry.title,
      content: entry.content,
      moodTag: entry.moodTag ?? '',
      entryDate: entry.entryDate,
    })
    setFormError(null)
    setFieldErrors([])
    setPanel('write')
  }

  async function onSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(null)
    setFieldErrors([])
    setSaving(true)
    const input = toInput(draft)
    try {
      const saved = draft.id ? await updateGratitude(draft.id, input) : await createGratitude(input)
      await refresh()
      spawn(saved)
      setDraft(emptyDraft())
      setPanel(null)
    } catch (caught) {
      if (!failAuth(caught)) {
        const parsed = readError(caught)
        setFormError(parsed.message)
        setFieldErrors(parsed.errors)
      }
    } finally {
      setSaving(false)
    }
  }

  async function onDelete(id: number) {
    setLoadError(null)
    try {
      await deleteGratitude(id)
      setPendingDeleteId(null)
      if (draft.id === id) {
        setDraft(emptyDraft())
      }
      const rows = await refresh()
      setSlots((current) => {
        const keptIds = new Set(rows.map((row) => row.id))
        const next = current.map((item) => (item && keptIds.has(item.id) ? item : null))
        if (next.every((item) => item === null)) {
          return filledSlots(rows)
        }
        return next
      })
    } catch (caught) {
      if (!failAuth(caught)) {
        setLoadError(readError(caught).message)
      }
    }
  }

  const empty = !loading && entries.length === 0

  function togglePanel(next: Panel) {
    setPanel((current) => (current === next ? null : next))
  }

  return (
    <div className="shell">
      <header className="topbar">
        <div>
          <p className="kicker">Praise</p>
          <p className="who">{user?.username}</p>
        </div>
        <div className="action-row">
          <button
            className={panel === 'write' ? 'action-button action-active' : 'action-button'}
            type="button"
            onClick={() => {
              setDraft(emptyDraft())
              setFormError(null)
              setFieldErrors([])
              togglePanel('write')
            }}
          >
            Write
          </button>
          <button
            className={panel === 'journal' ? 'action-button action-active' : 'action-button'}
            type="button"
            onClick={() => togglePanel('journal')}
          >
            Journal
            <span className="count">{entries.length}</span>
          </button>
          <button
            className="action-button"
            type="button"
            disabled={empty || loading}
            onClick={() => void summon(true)}
          >
            Summon
          </button>
          <button className="action-button" type="button" onClick={signOut}>
            Sign out
          </button>
        </div>
      </header>
      <section className="stage" aria-label="Floating memories">
        {empty ? (
          <div className="still">
            <p className="kicker">Nothing saved yet</p>
            <h1>Your journal is still.</h1>
            <p>Write the first note, and a memory can drift back to you.</p>
          </div>
        ) : (
          slots.map((entry, index) =>
            entry ? (
              <article key={index} className={`floating-note roam-${index}`} aria-hidden="true">
                {entry.moodTag ? <p className="note-mood">{entry.moodTag}</p> : null}
                <h2 className="note-title">{entry.title}</h2>
                <p className="note-body">{entry.content}</p>
                <p className="note-day">{formatDay(entry.entryDate)}</p>
              </article>
            ) : null,
          )
        )}
        {stageError ? (
          <p className="form-error stage-error" role="alert">
            {stageError}
          </p>
        ) : null}
      </section>
      {panel ? (
        <div className="sheet-backdrop" onClick={() => setPanel(null)}>
          <aside
            className="sheet"
            role="dialog"
            aria-label={panel === 'write' ? 'Write a note' : 'Journal'}
            onClick={(event) => event.stopPropagation()}
          >
            <div className="sheet-bar">
              <h2>{panel === 'write' ? (draft.id ? 'Revise a note' : 'Write a note') : 'Journal'}</h2>
              <button className="action-button" type="button" onClick={() => setPanel(null)}>
                Close
              </button>
            </div>
            {panel === 'write' ? (
              <form className="form composer" onSubmit={onSave}>
                <label className="field">
                  <span>Title</span>
                  <input
                    name="title"
                    maxLength={200}
                    required
                    value={draft.title}
                    onChange={(event) => setDraft({ ...draft, title: event.target.value })}
                  />
                  <FieldMessage errors={fieldErrors} field="title" />
                </label>
                <label className="field">
                  <span>Gratitude</span>
                  <textarea
                    name="content"
                    maxLength={4000}
                    required
                    rows={5}
                    value={draft.content}
                    onChange={(event) => setDraft({ ...draft, content: event.target.value })}
                  />
                  <FieldMessage errors={fieldErrors} field="content" />
                </label>
                <div className="field">
                  <span>Mood</span>
                  <div className="mood-row">
                    {MOODS.map((mood) => (
                      <button
                        key={mood}
                        className={draft.moodTag === mood ? 'chip chip-active' : 'chip'}
                        type="button"
                        onClick={() =>
                          setDraft({ ...draft, moodTag: draft.moodTag === mood ? '' : mood })
                        }
                      >
                        {mood}
                      </button>
                    ))}
                  </div>
                  <input
                    name="moodTag"
                    maxLength={50}
                    placeholder="Or a word of your own"
                    value={draft.moodTag}
                    onChange={(event) => setDraft({ ...draft, moodTag: event.target.value })}
                  />
                  <FieldMessage errors={fieldErrors} field="moodTag" />
                </div>
                <label className="field">
                  <span>Day</span>
                  <input
                    name="entryDate"
                    type="date"
                    value={draft.entryDate}
                    onChange={(event) => setDraft({ ...draft, entryDate: event.target.value })}
                  />
                  <small>Leave blank to use today.</small>
                  <FieldMessage errors={fieldErrors} field="entryDate" />
                </label>
                {formError ? (
                  <p className="form-error" role="alert">
                    {formError}
                  </p>
                ) : null}
                <div className="form-actions">
                  <button className="button" type="submit" disabled={saving}>
                    {saving ? 'Saving…' : draft.id ? 'Save changes' : 'Keep this note'}
                  </button>
                  {draft.id ? (
                    <button
                      className="button button-secondary"
                      type="button"
                      onClick={() => {
                        setDraft(emptyDraft())
                        setPanel(null)
                      }}
                    >
                      Cancel
                    </button>
                  ) : null}
                </div>
              </form>
            ) : (
              <div className="journal">
                {loading ? <p className="status">Gathering your notes…</p> : null}
                {loadError ? (
                  <p className="form-error" role="alert">
                    {loadError}
                  </p>
                ) : null}
                {!loading && entries.length === 0 ? (
                  <p className="journal-empty">No notes yet.</p>
                ) : null}
                <ul>
                  {entries.map((entry) => (
                    <li key={entry.id} className="entry">
                      <div className="entry-head">
                        <h3>{entry.title}</h3>
                        <time dateTime={entry.entryDate}>{formatDay(entry.entryDate)}</time>
                      </div>
                      {entry.moodTag ? <p className="note-mood">{entry.moodTag}</p> : null}
                      <p className="entry-body">{entry.content}</p>
                      <div className="entry-actions">
                        <button className="mini" type="button" onClick={() => beginEdit(entry)}>
                          Edit
                        </button>
                        {pendingDeleteId === entry.id ? (
                          <>
                            <button
                              className="mini mini-danger"
                              type="button"
                              onClick={() => void onDelete(entry.id)}
                            >
                              Delete
                            </button>
                            <button
                              className="mini"
                              type="button"
                              onClick={() => setPendingDeleteId(null)}
                            >
                              Keep
                            </button>
                          </>
                        ) : (
                          <button
                            className="mini"
                            type="button"
                            onClick={() => setPendingDeleteId(entry.id)}
                          >
                            Delete
                          </button>
                        )}
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </aside>
        </div>
      ) : null}
    </div>
  )
}

function FieldMessage({ errors, field }: { errors: FieldError[]; field: string }) {
  const match = errors.find((error) => error.field === field)
  if (!match) {
    return null
  }
  return <small className="field-error">{match.message}</small>
}

function toInput(draft: Draft): GratitudeInput {
  const mood = draft.moodTag.trim()
  return {
    entryDate: draft.entryDate || null,
    title: draft.title.trim(),
    content: draft.content.trim(),
    moodTag: mood || null,
  }
}

function readError(caught: unknown): { message: string; errors: FieldError[] } {
  if (caught instanceof ApiError) {
    return { message: caught.message, errors: caught.errors }
  }
  return { message: 'Something went wrong.', errors: [] }
}

function pickMemories(rows: Gratitude[]): Gratitude[] {
  const copy = [...rows]
  for (let i = copy.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1))
    const swap = copy[i]
    copy[i] = copy[j]
    copy[j] = swap
  }
  return copy.slice(0, Math.min(3, copy.length))
}

function filledSlots(rows: Gratitude[]): (Gratitude | null)[] {
  const picked = pickMemories(rows)
  return [picked[0] ?? null, picked[1] ?? null, picked[2] ?? null]
}

function formatDay(iso: string): string {
  const [year, month, day] = iso.split('-').map(Number)
  if (!year || !month || !day) {
    return iso
  }
  return new Intl.DateTimeFormat(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(year, month - 1, day))
}

function randomBetween(min: number, max: number): number {
  return min + Math.random() * (max - min)
}
