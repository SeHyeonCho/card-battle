#!/usr/bin/env bash
# 친구들과 플레이하기: 내 컴퓨터에서 서버를 띄우고 Cloudflare 터널로 인터넷 주소를 연다.
#
# 사용:  APP_ACCESS_CODE=정한코드 tools/serve.sh               # 터널까지 (cloudflared 필요: brew install cloudflared)
#        APP_ACCESS_CODE=정한코드 tools/serve.sh --no-tunnel   # 터널 없이 (같은 와이파이·테스트용)
#        PORT=8090 ...                                           # 포트 바꾸기 (기본 8080)
#
# 하는 일: 화면 빌드 → DB·Redis(docker compose) → 서버 빌드·실행(화면도 같은 주소로 내보냄) → 터널.
# Ctrl+C 로 서버와 터널을 함께 끈다 (DB·Redis 컨테이너는 남겨 둔다).
# 방장은 첫 화면 "방 만들기"에서 접근 코드를 입력하고, 친구들은 초대 링크로 들어온다 (코드 필요 없음).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${PORT:-8080}"
TUNNEL=1
[[ "${1:-}" == "--no-tunnel" ]] && TUNNEL=0

: "${APP_ACCESS_CODE:?방 만들기 접근 코드를 정해 주세요. 예: APP_ACCESS_CODE=비밀코드 tools/serve.sh}"
if [[ $TUNNEL == 1 ]] && ! command -v cloudflared >/dev/null; then
  echo "cloudflared 가 없습니다. 먼저 설치하세요: brew install cloudflared" >&2
  exit 1
fi
if lsof -ti "tcp:$PORT" -sTCP:LISTEN >/dev/null; then
  echo "포트 $PORT 를 이미 쓰는 프로그램이 있습니다 (개발용 bootRun 등). 먼저 끄거나 PORT=다른포트 로 실행하세요." >&2
  exit 1
fi

echo "[1/4] 화면 빌드"
(cd "$ROOT/frontend" && npm run build >/dev/null)

echo "[2/4] DB·Redis 켜기"
(cd "$ROOT" && docker compose up -d >/dev/null 2>&1)

echo "[3/4] 서버 빌드·실행 (포트 $PORT)"
(cd "$ROOT/backend" && ./gradlew -q :server:bootJar)
JAR="$(ls "$ROOT"/backend/server/build/libs/*.jar | grep -v -- '-plain' | head -1)"
LOG="$ROOT/backend/server/build/serve.log"
# 작업 폴더를 backend/server 로 해야 카드팩 자동 import 경로(../../packs/…)가 맞는다
(cd "$ROOT/backend/server" && exec env \
  APP_ACCESS_CODE="$APP_ACCESS_CODE" \
  APP_ALLOWEDORIGINS="http://localhost:[*],http://127.0.0.1:[*],https://*.trycloudflare.com" \
  SPRING_WEB_RESOURCES_STATICLOCATIONS="file:$ROOT/frontend/dist/" \
  SERVER_FORWARDHEADERSSTRATEGY=framework \
  SERVER_PORT="$PORT" \
  java -jar "$JAR" >"$LOG" 2>&1) &
SERVER_PID=$!
cleanup() {
  kill "$SERVER_PID" 2>/dev/null || true
  [[ -n "${TUNNEL_PID:-}" ]] && kill "$TUNNEL_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

for _ in $(seq 1 60); do
  grep -q "Started ServerApplication" "$LOG" 2>/dev/null && break
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "서버가 시작하지 못했습니다. 로그: $LOG" >&2
    tail -20 "$LOG" >&2
    exit 1
  fi
  sleep 1
done
echo "      서버 준비 완료: http://localhost:$PORT (로그: $LOG)"

if [[ $TUNNEL == 0 ]]; then
  IP="$(ipconfig getifaddr en0 2>/dev/null || true)"
  [[ -n "$IP" ]] && echo "      같은 와이파이의 친구는: http://$IP:$PORT"
  echo "Ctrl+C 로 종료"
  wait "$SERVER_PID"
  exit 0
fi

echo "[4/4] 인터넷 주소 여는 중 (Cloudflare 터널)…"
TUNNEL_LOG="$ROOT/backend/server/build/tunnel.log"
cloudflared tunnel --no-autoupdate --url "http://localhost:$PORT" >"$TUNNEL_LOG" 2>&1 &
TUNNEL_PID=$!
URL=""
for _ in $(seq 1 30); do
  URL="$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' "$TUNNEL_LOG" | head -1 || true)"
  [[ -n "$URL" ]] && break
  sleep 1
done
if [[ -z "$URL" ]]; then
  echo "터널 주소를 받지 못했습니다. 로그: $TUNNEL_LOG" >&2
  exit 1
fi
cat <<EOF

  ========================================================
   친구들에게 보낼 주소:  $URL
   방장: 이 주소에서 방 만들기 → 접근 코드 입력 → 초대 링크 복사
   (주소는 실행할 때마다 바뀝니다. Ctrl+C 로 종료)
  ========================================================

EOF
wait "$TUNNEL_PID"
