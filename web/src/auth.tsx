import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { login, me, readToken, register, writeToken, type User } from './api.ts'

type AuthState = {
  user: User | null
  ready: boolean
  signIn: (username: string, password: string) => Promise<void>
  signUp: (username: string, password: string) => Promise<void>
  signOut: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [ready, setReady] = useState(() => readToken() === null)

  useEffect(() => {
    if (!readToken()) {
      return
    }
    let cancelled = false
    me()
      .then((current) => {
        if (!cancelled) {
          setUser(current)
        }
      })
      .catch(() => {
        if (!cancelled) {
          writeToken(null)
          setUser(null)
        }
      })
      .finally(() => {
        if (!cancelled) {
          setReady(true)
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  const signIn = useCallback(async (username: string, password: string) => {
    const auth = await login(username, password)
    writeToken(auth.token)
    setUser(auth.user)
  }, [])

  const signUp = useCallback(async (username: string, password: string) => {
    const auth = await register(username, password)
    writeToken(auth.token)
    setUser(auth.user)
  }, [])

  const signOut = useCallback(() => {
    writeToken(null)
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ user, ready, signIn, signUp, signOut }),
    [user, ready, signIn, signUp, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// Shared with the provider so the rest of the app can read the session.
// oxlint-disable-next-line react/only-export-components
export function useAuth(): AuthState {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return value
}
