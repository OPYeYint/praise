export type User = {
  id: number
  username: string
}

export type AuthResponse = {
  token: string
  tokenType: string
  expiresAt: string
  user: User
}

export type Gratitude = {
  id: number
  entryDate: string
  title: string
  content: string
  moodTag: string | null
  createdAt: string
}

export type GratitudeInput = {
  entryDate: string | null
  title: string
  content: string
  moodTag: string | null
}

export type FieldError = {
  field: string
  message: string
}

type ProblemBody = {
  detail?: string
  title?: string
  errors?: FieldError[]
}

const TOKEN_KEY = 'praise.token'

export class ApiError extends Error {
  readonly status: number
  readonly errors: FieldError[]

  constructor(status: number, message: string, errors: FieldError[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errors = errors
  }
}

export function readToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function writeToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
    return
  }
  localStorage.removeItem(TOKEN_KEY)
}

export function register(username: string, password: string): Promise<AuthResponse> {
  return request<AuthResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function login(username: string, password: string): Promise<AuthResponse> {
  return request<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function me(): Promise<User> {
  return request<User>('/api/auth/me', { method: 'GET' }, true)
}

export function listGratitudes(): Promise<Gratitude[]> {
  return request<Gratitude[]>('/api/gratitudes', { method: 'GET' }, true)
}

export function randomGratitude(): Promise<Gratitude | null> {
  return request<Gratitude | null>('/api/gratitudes/random', { method: 'GET' }, true)
}

export function createGratitude(input: GratitudeInput): Promise<Gratitude> {
  return request<Gratitude>(
    '/api/gratitudes',
    { method: 'POST', body: JSON.stringify(input) },
    true,
  )
}

export function updateGratitude(id: number, input: GratitudeInput): Promise<Gratitude> {
  return request<Gratitude>(
    `/api/gratitudes/${id}`,
    { method: 'PUT', body: JSON.stringify(input) },
    true,
  )
}

export function deleteGratitude(id: number): Promise<void> {
  return request<void>(`/api/gratitudes/${id}`, { method: 'DELETE' }, true)
}

async function request<T>(path: string, options: RequestInit, authenticated = false): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (authenticated) {
    const token = readToken()
    if (!token) {
      throw new ApiError(401, 'Sign in again')
    }
    headers.set('Authorization', `Bearer ${token}`)
  }

  let response: Response
  try {
    response = await fetch(path, { ...options, headers })
  } catch {
    throw new ApiError(0, 'The journal server is not reachable. Start it on port 8082.')
  }

  if (response.status === 204) {
    return null as T
  }

  const text = await response.text()
  const body = parseBody(text)
  if (!response.ok) {
    const problem = body as ProblemBody | null
    const message = problem?.detail || problem?.title || 'Something went wrong'
    const errors = Array.isArray(problem?.errors) ? problem.errors : []
    throw new ApiError(response.status, message, errors)
  }
  return body as T
}

function parseBody(text: string): unknown {
  if (!text) {
    return null
  }
  try {
    return JSON.parse(text) as unknown
  } catch {
    return null
  }
}
