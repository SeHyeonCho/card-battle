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

- **원작 카드 데이터는 이 저장소에 절대 커밋하지 않는다.** 원작 팩은 별도 private 저장소에 두고
  `application.yml` 의 `app.packs.auto-import-dirs` 로 경로만 연결한다. `packs/private/`, `packs/original/` 은 gitignore 대상.
- 원작 이미지·사운드·BGM, 스타크래프트 리소스는 쓰지 않는다. 카드는 텍스트로, 효과음은 `tools/gen_sfx.py` 자체 제작.
- `packs/sample` 은 공개용 오리지널 카드만 둔다.

## 현재 상태 (Phase 1)

- 엔진: 완료, 테스트 56개 통과.
- 서버: `./gradlew build` 통과, `bootRun` 으로 기동 확인 (Flyway 마이그레이션, 카드팩 sample import 로그 정상).
- 프론트: 빌드·린트 통과. 실제 서버와 연결해 2인 한 판(방 만들기 → 참가 → 준비 → 시작 → 탈락 → 대기실 복귀) 확인.
- 한 브라우저에서 여러 명을 테스트할 때는 세션이 localStorage(origin 단위)에 있으므로 포트를 달리 띄운다
  (`npm run dev -- --port 5174`). Vite는 기본적으로 `localhost`(IPv6)에만 떠서 `127.0.0.1` 로는 안 열린다.

## 다음 작업 순서

1. 새로고침 복귀, 턴 시간 초과, 중복 요청 거절이 되는지 확인
2. 그다음 Phase 2: 저주, 필드 락(최대 2턴), 지속 상태, 추가 제출, 확률 효과 (PRD 8.3 P1 프리미티브)
