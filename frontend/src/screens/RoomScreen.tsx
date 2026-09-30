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
        <div className="text-sm text-sc-green">초대 코드</div>
        <div className="neon-gold mt-1 text-5xl font-bold tracking-[0.3em]">{room.inviteCode}</div>
        <button type="button" onClick={copyLink} className="btn mt-3 text-sm">
          🔗 초대 링크 복사
        </button>
      </div>

      <section className="frame frame-blue p-4">
        <div className="mb-3 flex items-center justify-between text-sm text-sky-200">
          <span>
            참가자 {room.members.length} / {room.settings.maxPlayers}
          </span>
          <span className={connected ? 'text-sc-green' : 'text-rose-400'}>{connected ? '● 연결됨' : '● 연결 중'}</span>
        </div>
        <ul className="space-y-1">
          {room.members.map((m) => (
            <li key={m.playerId} className="flex items-center justify-between border border-rim-steel bg-black/70 px-3 py-2">
              <span>
                {m.playerId === room.hostId && '👑 '}
                {m.nickname}
                {m.playerId === session.playerId && <span className="ml-1 text-xs text-sky-300">(나)</span>}
              </span>
              <span className={`text-sm ${m.playerId === room.hostId ? 'text-sc-yellow' : m.ready ? 'text-sc-green' : 'text-slate-500'}`}>
                {m.playerId === room.hostId ? '방장' : m.ready ? '준비 완료' : '대기 중'}
              </span>
            </li>
          ))}
        </ul>
      </section>

      <section className="grid grid-cols-4 gap-2 text-center text-xs text-sc-yellow">
        <Info label="카드팩" value={room.settings.packCode} />
        <Info label="시작 체력" value={String(room.settings.startingHp)} />
        <Info label="손패" value={`${room.settings.handSize}장`} />
        <Info label="턴 시간" value={`${room.settings.turnTimeSeconds}초`} />
      </section>

      {room.status === 'IN_GAME' && <div className="text-center text-sc-yellow">게임이 진행 중입니다. 불러오는 중...</div>}

      <div className="flex gap-2">
        {isHost ? (
          <button
            type="button"
            disabled={!canStart}
            onClick={startGame}
            className="btn btn-orange flex-1 py-3"
          >
            {room.members.length < 2 ? '2명 이상 필요' : allReady ? '게임 시작' : '모두 준비하면 시작'}
          </button>
        ) : (
          <button
            type="button"
            onClick={() => setReady(!me?.ready)}
            className={`btn flex-1 py-3 ${me?.ready ? '' : 'btn-green'}`}
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
          className="btn px-4 text-sm"
        >
          나가기
        </button>
      </div>
    </div>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="frame p-2">
      <div>{label}</div>
      <div className="mt-1 font-bold text-slate-100">{value}</div>
    </div>
  )
}
