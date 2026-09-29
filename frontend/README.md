# frontend

React + TypeScript + Vite. 실행 방법은 저장소 최상위 README를 보세요.

| 폴더 | 역할 |
|---|---|
| `src/screens` | 화면 3개: 첫 화면(Home), 대기실(Room), 게임(Game) |
| `src/components` | 카드, 좌석, 중앙 보드, 타이머, 로그, 결과 창 |
| `src/game/reduce.ts` | 서버 이벤트 → 화면 상태 반영 (규칙 계산 없음) |
| `src/game/actions.ts` | 카드 내기·버리기·준비·시작을 서버로 보냄 |
| `src/net/connection.ts` | STOMP 연결, 구독, 재접속 |
| `src/store/app.ts` | Zustand 전역 상태 + 연출(소리·떠오르는 숫자) 처리 |
| `src/audio/sfx.ts` | Howler 효과음 |
| `src/demo/demoGame.ts` | `?demo` 미리보기용 가짜 서버 |

```bash
npm run dev      # 개발 서버 (http://localhost:5173)
npm run build    # 타입 검사 + 빌드
npm run lint     # oxlint
```
