import { leaveRoom, setReady, startGame } from '../game/actions'
import { useApp } from '../store/app'

/** 대기실: 초대 링크, 참가자, 준비, 시작 (PRD 6.2) */
export function RoomScreen() {
  const room = useApp((s) => s.room)
  const session = useApp((s) => s.session)
  const connected = useApp((s) => s.connected)
  const toast = useApp((s) => s.toast)

  if (!room || !session) return null

  const me = room.members.find((m) => m.playerId === session.playerId)
  const isHost = room.hostId === session.playerId
  const allReady = room.members.every((m) => m.ready || m.playerId === room.hostId)
  const canStart = isHost && room.members.length >= 2 && allReady && room.status === 'LOBBY'
  const inviteLink = `${location.origin}/?invite=${room.inviteCode}`

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(inviteLink)
      toast('초대 링크를 복사했어요')
    } catch {
      toast(inviteLink)
    }
  }

  return (
    <div className="mx-auto flex min-h-full max-w-lg flex-col justify-center gap-5 p-6">
      <div className="text-center">
        <div className="text-sm text-slate-400">초대 코드</div>
        <div className="font-mono text-5xl font-black tracking-[0.3em] text-amber-300">{room.inviteCode}</div>
        <button type="button" onClick={copyLink} className="mt-2 rounded-lg bg-slate-800 px-3 py-1 text-sm hover:bg-slate-700">
          🔗 초대 링크 복사
        </button>
      </div>

      <section className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
        <div className="mb-2 flex items-center justify-between text-sm text-slate-400">
          <span>
            참가자 {room.members.length} / {room.settings.maxPlayers}
          </span>
          <span className={connected ? 'text-emerald-400' : 'text-rose-400'}>{connected ? '● 연결됨' : '● 연결 중'}</span>
        </div>
        <ul className="space-y-1">
          {room.members.map((m) => (
            <li key={m.playerId} className="flex items-center justify-between rounded-lg bg-slate-800/70 px-3 py-2">
              <span>
                {m.playerId === room.hostId && '👑 '}
                {m.nickname}
                {m.playerId === session.playerId && <span className="ml-1 text-xs text-sky-300">(나)</span>}
              </span>
              <span className={`text-sm ${m.ready || m.playerId === room.hostId ? 'text-emerald-400' : 'text-slate-500'}`}>
                {m.playerId === room.hostId ? '방장' : m.ready ? '준비 완료' : '대기 중'}
              </span>
            </li>
          ))}
        </ul>
      </section>

      <section className="grid grid-cols-4 gap-2 text-center text-xs text-slate-400">
        <Info label="카드팩" value={room.settings.packCode} />
        <Info label="시작 체력" value={String(room.settings.startingHp)} />
        <Info label="손패" value={`${room.settings.handSize}장`} />
        <Info label="턴 시간" value={`${room.settings.turnTimeSeconds}초`} />
      </section>

      {room.status === 'IN_GAME' && <div className="text-center text-amber-300">게임이 진행 중입니다. 불러오는 중...</div>}

      <div className="flex gap-2">
        {isHost ? (
          <button
            type="button"
            disabled={!canStart}
            onClick={startGame}
            className="flex-1 rounded-lg bg-amber-500 py-3 font-bold text-slate-950 hover:bg-amber-400 disabled:opacity-40"
          >
            {room.members.length < 2 ? '2명 이상 필요' : allReady ? '게임 시작' : '모두 준비하면 시작'}
          </button>
        ) : (
          <button
            type="button"
            onClick={() => setReady(!me?.ready)}
            className={`flex-1 rounded-lg py-3 font-bold ${me?.ready ? 'bg-slate-700 text-slate-200' : 'bg-emerald-500 text-slate-950 hover:bg-emerald-400'}`}
          >
            {me?.ready ? '준비 취소' : '준비'}
          </button>
        )}
        <button
          type="button"
          onClick={() => {
            leaveRoom()
            location.href = '/'
          }}
          className="rounded-lg bg-slate-800 px-4 text-sm hover:bg-slate-700"
        >
          나가기
        </button>
      </div>
    </div>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-slate-900/60 p-2">
      <div>{label}</div>
      <div className="mt-0.5 font-semibold text-slate-200">{value}</div>
    </div>
  )
}
