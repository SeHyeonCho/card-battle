import { useEffect, useRef, useState } from 'react'
import { ApiError, api } from '../api/http'
import { useApp } from '../store/app'
import type { PackSummary } from '../types'

/** 첫 화면: 닉네임 → 초대 코드로 참가 또는 방 만들기 (PRD 4.2) */
export function HomeScreen({ initialInvite }: { initialInvite: string }) {
  const session = useApp((s) => s.session)
  const setSession = useApp((s) => s.setSession)
  const enterRoom = useApp((s) => s.enterRoom)
  const toast = useApp((s) => s.toast)

  const [nickname, setNickname] = useState(session?.nickname ?? '')
  const [invite, setInvite] = useState(initialInvite)
  const [accessCode, setAccessCode] = useState('')
  const [packs, setPacks] = useState<PackSummary[]>([])
  const [packCode, setPackCode] = useState('sample')
  const [maxPlayers, setMaxPlayers] = useState(4)
  const [startingHp, setStartingHp] = useState(200)
  const [turnTime, setTurnTime] = useState(25)
  const [busy, setBusy] = useState(false)
  // 방 만들기 영역. 열면 곧바로 접근 코드 칸에 포커스를 준다.
  // (예전 <details>는 열고 나서도 포커스가 제목 줄에 남아, 바로 타이핑하면 글자는 안 들어가고
  //  스페이스·엔터가 제목 줄을 눌러 영역이 다시 닫혔다)
  const [createOpen, setCreateOpen] = useState(false)
  const accessCodeInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (createOpen) accessCodeInput.current?.focus()
  }, [createOpen])

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

  const loadPacks = () =>
    run(async () => {
      const list = await api.listPacks(accessCode)
      setPacks(list)
      if (list.length && !list.some((p) => p.code === packCode)) setPackCode(list[0].code)
    })

  const create = () =>
    run(async () => {
      const s = await ensureSession()
      enterRoom(await api.createRoom(s.token, accessCode, { packCode, maxPlayers, startingHp, hpCap: Math.max(500, startingHp), turnTimeSeconds: turnTime }))
    })

  const nicknameOk = nickname.trim().length >= 2 && nickname.trim().length <= 12
  const input = 'field-input'
  const primary = 'btn btn-orange w-full py-2'

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col justify-center gap-6 p-6">
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

      <section className="frame frame-orange p-4">
        <button
          type="button"
          className="flex w-full cursor-pointer items-center gap-2 text-left font-bold text-orange-200"
          aria-expanded={createOpen}
          aria-controls="create-room"
          onClick={() => setCreateOpen((open) => !open)}
        >
          <span className="text-xs text-orange-400" aria-hidden>
            {createOpen ? '▼' : '▶'}
          </span>
          방 만들기 (방장)
        </button>
        <div id="create-room" className="mt-3 space-y-3" hidden={!createOpen}>
          <div className="flex gap-2">
            <input ref={accessCodeInput} className={input} type="password" value={accessCode} onChange={(e) => setAccessCode(e.target.value)} placeholder="서버 접근 코드" />
            <button type="button" className="btn shrink-0 text-sm" onClick={loadPacks} disabled={busy}>
              팩 불러오기
            </button>
          </div>
          <label className="block space-y-1 text-sm text-sc-yellow">
            카드팩
            <select className={input} value={packCode} onChange={(e) => setPackCode(e.target.value)}>
              {packs.length === 0 && <option value="sample">sample (기본)</option>}
              {packs.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.name} v{p.version} {p.visibility === 'PRIVATE' ? '🔒' : ''}
                </option>
              ))}
            </select>
          </label>
          <div className="grid grid-cols-3 gap-2 text-sm text-sc-yellow [&_label]:space-y-1 [&_select]:mt-1">
            <label>
              최대 인원
              <select className={input} value={maxPlayers} onChange={(e) => setMaxPlayers(Number(e.target.value))}>
                {[2, 3, 4, 5, 6].map((n) => (
                  <option key={n} value={n}>
                    {n}명
                  </option>
                ))}
              </select>
            </label>
            <label>
              시작 체력
              <select className={input} value={startingHp} onChange={(e) => setStartingHp(Number(e.target.value))}>
                {[100, 200, 300, 500].map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
            </label>
            <label>
              턴 시간
              <select className={input} value={turnTime} onChange={(e) => setTurnTime(Number(e.target.value))}>
                {[15, 25, 40].map((n) => (
                  <option key={n} value={n}>
                    {n}초
                  </option>
                ))}
              </select>
            </label>
          </div>
          <button type="button" className={primary} disabled={busy || !nicknameOk || !accessCode} onClick={create}>
            방 만들기
          </button>
        </div>
      </section>

      <a href="/?demo" className="text-center text-sm text-slate-500 underline hover:text-sc-yellow">
        서버 없이 게임 화면 미리보기
      </a>
    </div>
  )
}
