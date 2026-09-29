import type { PackSummary, Room, RoomSettings, Session } from '../types'

/** 서버 오류 응답 {code, message} */
export class ApiError extends Error {
  readonly code: string
  readonly status: number

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

async function request<T>(method: string, path: string, body?: unknown, headers: Record<string, string> = {}): Promise<T> {
  const res = await fetch(path, {
    method,
    headers: { 'Content-Type': 'application/json', ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!res.ok) {
    const data = await res.json().catch(() => ({}))
    throw new ApiError(res.status, data.code ?? 'UNKNOWN', data.message ?? `요청 실패 (${res.status})`)
  }
  return res.json() as Promise<T>
}

const auth = (token: string) => ({ 'X-Session-Token': token })

export const api = {
  createSession: (nickname: string) => request<Session>('POST', '/api/sessions', { nickname }),

  listPacks: (accessCode?: string) =>
    request<PackSummary[]>('GET', '/api/packs', undefined, accessCode ? { 'X-Access-Code': accessCode } : {}),

  createRoom: (token: string, accessCode: string, settings: Partial<RoomSettings>) =>
    request<Room>('POST', '/api/rooms', settings, { ...auth(token), 'X-Access-Code': accessCode }),

  joinRoom: (token: string, inviteCode: string) =>
    request<Room>('POST', `/api/rooms/${encodeURIComponent(inviteCode)}/join`, undefined, auth(token)),
}
