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

  /** 카드팩을 보내지 않으면 서버 기본 팩(원작 팩이 있으면 원작, 없으면 샘플). 접근 코드는 서버가 요구할 때만 */
  createRoom: (token: string, settings: Partial<RoomSettings>, accessCode?: string) =>
    request<Room>('POST', '/api/rooms', settings, { ...auth(token), ...(accessCode ? { 'X-Access-Code': accessCode } : {}) }),

  /** 카드 ID → 효과음 주소. 카드별 소리가 없는 팩이면 빈 객체 */
  cardSounds: (packCode: string) => request<Record<string, string>>('GET', `/api/packs/${encodeURIComponent(packCode)}/sounds`),
  cardImages: (packCode: string) => request<Record<string, string>>('GET', `/api/packs/${encodeURIComponent(packCode)}/images`),

  joinRoom: (token: string, inviteCode: string) =>
    request<Room>('POST', `/api/rooms/${encodeURIComponent(inviteCode)}/join`, undefined, auth(token)),
}
