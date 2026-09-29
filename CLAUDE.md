# 카드 배틀 웹 — Claude Code 작업 지침

스타크래프트 유즈맵 '랜덤카드배틀' 규칙을 웹으로 옮긴 실시간 멀티플레이 카드 게임.
기획은 `docs/PRD.md`(규칙 명세는 7장, 카드 스펙은 8장), 배경은 `docs/context.md`.
필요할 때만 열어 본다 (길어서 매번 읽지 않는다).

## 명령어

```bash
docker compose up -d                                  # PostgreSQL + Redis
cd backend && ./gradlew :engine:test                  # 규칙 엔진 테스트 (서버 없이)
cd backend && ./gradlew build                         # 전체 빌드
cd backend && APP_ACCESS_CODE=test ./gradlew :server:bootRun   # 서버 :8080
cd frontend && npm run dev                            # 프론트 :5173 (/api, /ws 는 8080으로 프록시)
cd frontend && npm run build && npm run lint          # 타입 검사 + 빌드 + oxlint
python3 tools/gen_sfx.py                              # 효과음 다시 만들기
```

서버 없이 화면만 확인: http://localhost:5173/?demo

## 구조와 절대 규칙

- `backend/engine` 은 **순수 Java**. Spring·Redis·Jackson 등 어떤 라이브러리도 추가하지 않는다.
  JSON 파싱은 서버가 하고, 엔진은 `Map<String,Object>` 만 받는다 (`PackParser`).
- **모든 게임 판정은 엔진에서.** 서버는 "상태 읽기 → 엔진 호출 → 버전 CAS 저장 → 이벤트 전송"만 한다.
  프론트는 규칙을 계산하지 않고 서버 이벤트를 그대로 반영한다 (`frontend/src/game/reduce.ts`).
  예외: `frontend/src/demo/` 는 `?demo` 미리보기 전용 흉내이며 실제 규칙이 아니다.
- 규칙을 바꾸거나 효과를 추가하면 **반드시 `engine/src/test` 에 테스트를 추가**하고 `:engine:test` 를 통과시킨다.
  `RandomPlayoutTest` 는 수백 판을 무작위로 돌리는 안전망이니 깨지면 원인을 찾는다.
- 새 효과: `EffectHandler` 구현 → `EffectRegistry.defaults()` 에 등록 → `validate()` 로 파라미터 검사 → 테스트.
  등록 안 된 효과 타입을 쓴 카드팩은 import에서 거부되는 것이 정상 동작이다.

## 확정된 규칙 (PRD 16장 D7·D8·D12)

- 현재 공격력(A)과 **같거나 높은** 공격 카드: 누적(D)에 더하고 넘긴다.
- **낮은** 공격 카드: 현재 D를 전부 받고, 그 카드로 새 체인 시작 (A = p, D = p).
- 공격이 아닌 카드·버리기: 남은 D를 받고 체인 종료 (A = 0, D = 0). 버리기는 필드를 바꾸지 않는다.
- 효과·조건은 **직전 필드**를 보고, 필드 교체는 누적 판정 뒤에 한다.
- 탈락은 정산 중 한 번에 판정한다 (0 이하로 내려갔다가 회복하면 생존).

## 코드 규칙과 함정

- 백엔드는 **Spring Boot 4.1 + Java 21**. Spring Boot 3 자료를 그대로 쓰지 않는다:
  - 스타터 이름이 다르다 (`spring-boot-starter-webmvc`, `-flyway`, `-jdbc` 등)
  - Jackson 3: 패키지가 `tools.jackson.*`, 예외는 unchecked. 어노테이션만 `com.fasterxml.jackson.annotation` 그대로
- Redis에 JSON으로 저장되는 클래스(`GameState`, `PlayerState`, `Room`, `RoomMember`)는 기본 생성자 + getter/setter를 유지하고,
  **계산용 메서드에 `get`/`is` 접두사를 붙이지 않는다** (JSON 속성으로 오인됨). 예: `alive()`, `currentPlayer()`
- 서버 → 클라이언트 게임 메시지는 항상 `{type, payload, seq?, version?}` 모양. 새 이벤트는 엔진 `EventType` 에 추가하고
  `frontend/src/game/reduce.ts` 에서 처리한다.
- 코드 주석과 사용자에게 보이는 문구는 한국어.
- 프론트: `tsconfig` 에 `erasableSyntaxOnly` 가 켜져 있어 TS `enum` 과 생성자 매개변수 속성을 쓸 수 없다 (문자열 유니언 사용).

## 저작권·데이터 규칙

- **원작 카드 데이터는 이 저장소에 절대 커밋하지 않는다.** 원작 팩은 `packs/original/`(gitignore, 로컬 전용)에 두고
  서버는 `app.packs.auto-import-dirs` 로 읽는다. 폴더가 없으면 경고만 남기고 넘어간다.
  카드 JSON은 `packs/original/generate.py` 로 다시 만든다 (출처: 나무위키 'EUD 랜덤카드배틀' 카드 목록).
- 원작 이미지·사운드·BGM은 **비공개 플레이 전용**으로 쓴다. 파일은 `packs/original/assets/`(gitignore)에만 두고 커밋하지 않는다.
  공개 저장소·공개 배포·`packs/sample` 에는 텍스트 카드와 `tools/gen_sfx.py` 자체 제작 효과음만 쓴다.
- `packs/sample` 은 공개용 오리지널 카드만 둔다.
- 카드팩 폴더에 `assets/sounds/manifest.json`(카드 ID → mp3)이 있으면 서버가 `/api/packs/{code}/sounds` 로 제공하고,
  화면은 카드를 낼 때 그 소리를 재생한다 (없으면 기본 효과음). 원작 소리 파일은 로컬 전용.

## 작업 방식 (사용자와 합의한 것)

- 답변·중간 보고·최종 요약은 **모두 한국어**로 쓴다 (코드·명령어·경로는 그대로).
- **PR을 만들지 않는다.** 브랜치에서 작업 → `main` 에 fast-forward 병합 → `git push origin main`.
  PR은 사용자가 명시적으로 요청할 때만 만든다.
- 병합이 끝난 브랜치는 **삭제하기 전에 사용자에게 물어본다.**
- 커밋은 기능 단위로 나누고 메시지는 한국어로 쓴다.
- 공개 저장소이므로 문서·화면 문구에 사용 대상을 특정하는 표현을 쓰지 않는다 (원작 리소스는 "비공개 플레이 전용").

## 로컬 전용 파일 (클라우드 세션·새로 받은 저장소에는 없다)

`packs/original/` 은 gitignore 대상이라 GitHub에 없다. 원작 카드·효과음 작업은 이 폴더가 있는 로컬에서만 할 수 있다.

| 경로 | 내용 |
|---|---|
| `packs/original/generate.py` → `pack.json`, `cards/*.json` | 원작 카드 145장 (출처: 나무위키 'EUD 랜덤카드배틀') |
| `packs/original/assets/raw/` | 원작 맵 파일 1.14 / 1.19.1 (scmscx.com) |
| `packs/original/assets/extracted/` | 맵에서 추출한 소리 (`mpq.py`, StormLib 필요: `brew install stormlib`) |
| `packs/original/assets/review/` | 효과음 검토 페이지 (`.claude/launch.json` 의 `original-review` → `/review/`) |
| `packs/original/assets/picks.json` → `build_sounds.py` → `sounds/` | 고른 효과음 59개 mp3 + `manifest.json` |

폴더가 없으면 서버는 경고만 남기고 샘플 팩으로 동작하며, `OriginalPackPlayoutTest` 는 건너뛴다.

## 현재 상태 (Phase 2 완료)

- 엔진: Phase 2 효과까지 완료 (PRD 8.3 프리미티브 + CUSTOM 6종), 엔진 테스트 140개 통과.
  원작 팩 145장이 import되고, `server` 테스트가 원작 팩으로 무작위 400판을 돌린다 (packs/original이 없으면 건너뜀).
- 서버: `./gradlew build` 통과, `bootRun` 으로 기동 확인 (Flyway 마이그레이션, 카드팩 import 로그 정상).
  원작 팩이 있으면 카드별 효과음 59개를 등록해 `/api/packs/{code}/sounds` 로 제공한다.
- 프론트: 빌드·린트 통과. 실제 서버와 연결해 2인 한 판(방 만들기 → 참가 → 준비 → 시작 → 탈락 → 대기실 복귀) 확인.
  Phase 2 화면(저주·지속 상태·필드 락·시한폭탄·카운트다운·공개 손패·추가 제출 안내)을 원작 팩 실제 플레이로 확인.
- 한 브라우저에서 여러 명을 테스트할 때는 세션이 localStorage(origin 단위)에 있으므로 포트를 달리 띄운다
  (`npm run dev -- --port 5174`). Vite는 기본적으로 `localhost`(IPv6)에만 떠서 `127.0.0.1` 로는 안 열린다.
- 복구·방어 동작 확인 완료 (실제 서버 + 브라우저 2개):
  - 새로고침·탭 재오픈 → 같은 좌석·체력·A/D·필드·손패·차례로 복귀 (방 대기실도 복귀)
  - 턴 시간 초과 → `TURN_TIMED_OUT`, 무작위 1장 버리고 차례 넘어감
  - 옛 버전 `STALE_VERSION`, 같은 actionId `DUPLICATE_ACTION`(한 번만 처리), 남의 차례 `NOT_YOUR_TURN`,
    1초 6번째 요청부터 `RATE_LIMITED`
- 알려진 개선점: 새로고침하면 게임 로그 패널이 비어 있다 (스냅샷에 로그가 없음, FR-UI-04 P1)

## 다음 작업 순서

1. 원작 이미지 연결 (효과음은 연결 완료: `packs/original/assets/picks.json` → `build_sounds.py` → `assets/sounds/`)
2. 새로고침하면 게임 로그가 비는 문제 (스냅샷에 최근 로그 포함)
3. 원작 카드 데이터를 고치면 `packs/original/generate.py` 의 pack `version` 을 올린다 (같은 버전은 다시 import하지 않음)
