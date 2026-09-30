import { useState } from 'react'
import { ApiError, api } from '../api/http'
import { MAX_PLAYER_OPTIONS, STARTING_HP_OPTIONS, TURN_TIME_OPTIONS, hpCapFor } from '../roomOptions'
import { useApp } from '../store/app'

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
      enterRoom(await api.createRoom(s.token, { maxPlayers, startingHp, hpCap: hpCapFor(startingHp), turnTimeSeconds: turnTime }))
    })

  const nicknameOk = nickname.trim().length >= 2 && nickname.trim().length <= 12
  const input = 'field-input'
  const primary = 'btn btn-orange w-full py-2'

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col justify-center gap-6 px-6 pt-14 pb-6">
      <div className="text-center">
        <h1 className="neon-gold text-5xl font-bold">카드 배틀</h1>
        <p className="mt-3 text-sc-green">누적 데미지를 상대에게 떠넘겨라</p>
      </div>

      <section className="space-y-2">
        <label className="text-sm text-sc-yellow">닉네임 (2~12자)</label>
        <input className={input} value={nickname} maxLength={12} onChange={(e) => setNickname(e.target.value)} placeholder="닉네임" />
      </section>

      <section className="frame frame-blue space-y-3 p-4">
        <h2 className="font-bold text-sky-200">초대 코드로 참가</h2>
        <input className={`${input} tracking-[0.3em] uppercase`} value={invite} maxLength={6} onChange={(e) => setInvite(e.target.value)} placeholder="ABC234" />
        <button type="button" className={primary} disabled={busy || !nicknameOk || invite.trim().length !== 6} onClick={join}>
          참가하기
        </button>
      </section>

      <section className="frame frame-orange space-y-3 p-4">
        <h2 className="font-bold text-orange-200">방 만들기 (방장)</h2>
        <div className="grid grid-cols-3 gap-2 text-sm text-sc-yellow [&_label]:space-y-1 [&_select]:mt-1">
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
        <button type="button" className={primary} disabled={busy || !nicknameOk} onClick={create}>
          방 만들기
        </button>
      </section>

      <a href="/?demo" className="text-center text-sm text-slate-500 underline hover:text-sc-yellow">
        서버 없이 게임 화면 미리보기
      </a>

      <p className="text-center text-[11px] leading-relaxed text-slate-500">
        BGM: "Fluffing a Duck" Kevin MacLeod (
        <a href="https://incompetech.com" target="_blank" rel="noreferrer" className="underline hover:text-sc-yellow">
          incompetech.com
        </a>
        ) ·{' '}
        <a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noreferrer" className="underline hover:text-sc-yellow">
          CC BY 4.0
        </a>
      </p>
    </div>
  )
}
