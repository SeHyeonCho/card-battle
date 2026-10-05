import { useEffect, useState } from 'react'
import { ApiError, api } from '../api/http'
import { MAX_PLAYER_OPTIONS, STARTING_HP_OPTIONS, TURN_TIME_OPTIONS, hpCapFor } from '../roomOptions'
import { useApp } from '../store/app'
import { readStorage, writeStorage } from '../util'

/** 방장이 한 번 입력한 접근 코드를 이 브라우저에 기억해 둔다 */
const ACCESS_CODE_KEY = 'access-code'

/** 첫 화면: 닉네임 → 초대 코드로 참가 또는 방 만들기 (PRD 4.2) */
export function HomeScreen({ initialInvite }: { initialInvite: string }) {
  const session = useApp((s) => s.session)
  const setSession = useApp((s) => s.setSession)
  const enterRoom = useApp((s) => s.enterRoom)
  const toast = useApp((s) => s.toast)

  const [nickname, setNickname] = useState(session?.nickname ?? '')
  const [invite, setInvite] = useState(initialInvite)
  const [maxPlayers, setMaxPlayers] = useState(4)
  const [startingHp, setStartingHp] = useState(200)
  const [turnTime, setTurnTime] = useState(25)
  const [busy, setBusy] = useState(false)
  // 서버가 접근 코드를 요구할 때만(공개 배포) 방 만들기에 입력칸을 보여 준다
  const [accessCodeRequired, setAccessCodeRequired] = useState(false)
  const [accessCode, setAccessCode] = useState(() => readStorage<string>(ACCESS_CODE_KEY) ?? '')

  useEffect(() => {
    api
      .config()
      .then((c) => setAccessCodeRequired(c.accessCodeRequired))
      .catch(() => {})
  }, [])

  async function run(action: () => Promise<void>) {
    setBusy(true)
    try {
      await action()
    } catch (e) {
      if (e instanceof ApiError && e.code === 'INVALID_SESSION') {
        setSession(null)
      }
      toast(e instanceof Error ? e.message : '요청에 실패했습니다')
    } finally {
      setBusy(false)
    }
  }

  async function ensureSession() {
    if (session && session.nickname === nickname.trim()) return session
    const created = await api.createSession(nickname.trim())
    setSession(created)
    return created
  }

  const join = () =>
    run(async () => {
      const s = await ensureSession()
      enterRoom(await api.joinRoom(s.token, invite.trim().toUpperCase()))
    })

  const create = () =>
    run(async () => {
      const s = await ensureSession()
      // 카드팩은 서버 기본 팩을 쓴다 (원작 팩이 있으면 원작)
      const settings = { maxPlayers, startingHp, hpCap: hpCapFor(startingHp), turnTimeSeconds: turnTime }
      const room = await api.createRoom(s.token, settings, accessCodeRequired ? accessCode.trim() : undefined)
      if (accessCodeRequired) writeStorage(ACCESS_CODE_KEY, accessCode.trim())
      enterRoom(room)
    })

  const nicknameOk = nickname.trim().length >= 2 && nickname.trim().length <= 12
  const input = 'field-input'
  const primary = 'btn btn-primary w-full py-3'

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col justify-center gap-4 px-5 pt-16 pb-8">
      <div className="mb-4">
        <h1 className="text-5xl font-extrabold tracking-tight">카드 배틀</h1>
        <p className="mt-2 text-muted">누적 데미지를 상대에게 떠넘겨라</p>
      </div>

      <section className="space-y-2">
        <label className="label">닉네임 (2~12자)</label>
        <input className={input} value={nickname} maxLength={12} onChange={(e) => setNickname(e.target.value)} placeholder="닉네임" />
      </section>

      <section className="panel space-y-3 p-5">
        <h2 className="font-bold">초대 코드로 참가</h2>
        <input className={`${input} font-semibold tracking-[0.3em] uppercase`} value={invite} maxLength={6} onChange={(e) => setInvite(e.target.value)} placeholder="ABC234" />
        <button type="button" className={primary} disabled={busy || !nicknameOk || invite.trim().length !== 6} onClick={join}>
          참가하기
        </button>
      </section>

      <section className="panel space-y-3 p-5">
        <h2 className="font-bold">방 만들기</h2>
        {accessCodeRequired && (
          <input
            className={input}
            type="password"
            value={accessCode}
            onChange={(e) => setAccessCode(e.target.value)}
            placeholder="서버 접근 코드"
            autoComplete="off"
          />
        )}
        <div className="grid grid-cols-3 gap-2 text-xs font-semibold text-muted [&_select]:mt-1.5">
          <label>
            최대 인원
            <select className={input} value={maxPlayers} onChange={(e) => setMaxPlayers(Number(e.target.value))}>
              {MAX_PLAYER_OPTIONS.map((n) => (
                <option key={n} value={n}>
                  {n}명
                </option>
              ))}
            </select>
          </label>
          <label>
            시작 체력
            <select className={input} value={startingHp} onChange={(e) => setStartingHp(Number(e.target.value))}>
              {STARTING_HP_OPTIONS.map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </label>
          <label>
            턴 시간
            <select className={input} value={turnTime} onChange={(e) => setTurnTime(Number(e.target.value))}>
              {TURN_TIME_OPTIONS.map((n) => (
                <option key={n} value={n}>
                  {n}초
                </option>
              ))}
            </select>
          </label>
        </div>
        <button type="button" className="btn w-full py-3" disabled={busy || !nicknameOk || (accessCodeRequired && !accessCode.trim())} onClick={create}>
          방 만들기
        </button>
      </section>

      <a href="/?demo" className="mt-2 text-center text-sm text-muted underline hover:text-ink">
        서버 없이 게임 화면 미리보기
      </a>

      <p className="text-center text-[11px] leading-relaxed text-muted/70">
        BGM: "Fluffing a Duck" Kevin MacLeod (
        <a href="https://incompetech.com" target="_blank" rel="noreferrer" className="underline hover:text-ink">
          incompetech.com
        </a>
        ) ·{' '}
        <a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noreferrer" className="underline hover:text-ink">
          CC BY 4.0
        </a>
      </p>
    </div>
  )
}
