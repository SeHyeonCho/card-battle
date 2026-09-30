import { useEffect, useState } from 'react'
import { api } from './api/http'
import { bgm } from './audio/bgm'
import { sfx } from './audio/sfx'
import { BgmToggle } from './components/BgmToggle'
import { Toasts } from './components/Toasts'
import { startDemo } from './game/actions'
import { connect, disconnect } from './net/connection'
import { GameScreen } from './screens/GameScreen'
import { HomeScreen } from './screens/HomeScreen'
import { RoomScreen } from './screens/RoomScreen'
import { INVITE_KEY, useApp } from './store/app'
import { readStorage, writeStorage } from './util'

const params = new URLSearchParams(location.search)
const DEMO = params.has('demo')
const INVITE_FROM_URL = (params.get('invite') ?? '').toUpperCase()

/** 새로고침 전에 들어가 있던 방의 초대 코드. 다시 참가할 게 없으면 null */
function rejoinCode(hasSession: boolean): string | null {
  const lastInvite = readStorage<string>(INVITE_KEY)
  const code = INVITE_FROM_URL || lastInvite
  if (!hasSession || !code || (INVITE_FROM_URL && INVITE_FROM_URL !== lastInvite)) {
    return null
  }
  return code
}

export default function App() {
  const screen = useApp((s) => s.screen)
  const session = useApp((s) => s.session)
  const [booted, setBooted] = useState(() => DEMO || rejoinCode(useApp.getState().session !== null) === null)

  // 배경음악: 첫 화면·대기실은 대기 곡, 게임 중에는 끈다
  useEffect(() => {
    bgm.play(screen === 'game' ? null : 'lobby')
  }, [screen])

  // 버튼 클릭음: 화면의 버튼(.btn)을 누르면 딸깍. 카드는 카드 소리가 따로 나서 제외된다.
  // 브라우저는 첫 클릭 전까지 소리를 막으므로, 클릭할 때 멈춰 있던 배경음악도 이어 튼다
  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      bgm.resume()
      if (e.target instanceof Element && e.target.closest('button.btn:not(:disabled)')) sfx.play('click')
    }
    document.addEventListener('click', onClick)
    return () => document.removeEventListener('click', onClick)
  }, [])

  // ?demo: 서버 없이 게임 화면 미리보기
  useEffect(() => {
    if (DEMO && !useApp.getState().game) startDemo()
  }, [])

  // 세션이 있으면 STOMP 연결
  useEffect(() => {
    if (DEMO || !session) return
    const app = useApp.getState()
    connect(session.token, {
      onRoom: app.handleRoomMessage,
      onGame: app.handleGameMessage,
      onStatus: app.setConnected,
      onError: (e) => {
        if (e.code === 'STOMP_ERROR') {
          // 세션 만료 등으로 연결이 거부됨 → 처음 화면으로
          disconnect()
          writeStorage(INVITE_KEY, null)
          useApp.setState({ screen: 'home', room: null, game: null })
          app.setSession(null)
          app.toast('세션이 만료됐어요. 닉네임을 다시 입력해 주세요')
        } else {
          app.toast(e.message)
        }
      },
    })
  }, [session])

  // 새로고침 복귀: 전에 들어가 있던 방이 있으면 다시 참가 (FR-AUTH-03)
  useEffect(() => {
    const code = rejoinCode(session !== null)
    if (booted || !session || !code) return
    api
      .joinRoom(session.token, code)
      .then((room) => useApp.getState().enterRoom(room))
      .catch(() => writeStorage(INVITE_KEY, null))
      .finally(() => setBooted(true))
  }, [session, booted])

  if (!booted) {
    return <div className="flex h-full items-center justify-center text-sc-yellow">불러오는 중...</div>
  }

  return (
    <>
      {screen === 'home' && <HomeScreen initialInvite={INVITE_FROM_URL} />}
      {screen === 'room' && <RoomScreen />}
      {screen === 'game' && <GameScreen />}
      {screen !== 'game' && <BgmToggle />}
      <Toasts />
    </>
  )
}
