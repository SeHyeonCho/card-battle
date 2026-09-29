# 랜덤카드배틀

스타크래프트 유즈맵 '랜덤카드배틀'의 규칙을 웹으로 옮긴 실시간 멀티플레이 카드 게임.
카드 내용은 DB의 **카드팩** 데이터라서, 엔진 코드를 고치지 않고 팩만 바꿔 끼울 수 있다.

- 기획: [`docs/PRD.md`](docs/PRD.md) · 배경과 원작 조사: [`docs/context.md`](docs/context.md)
- 현재 단계: **Phase 1 (MVP) 뼈대**

## 폴더 구조

```
card-battle-web/
├─ backend/                  Gradle 멀티모듈 (Java 21, Spring Boot 4.1)
│  ├─ engine/                순수 Java 규칙 엔진 + 테스트 (Spring 의존 없음)
│  └─ server/                Spring Boot: REST, STOMP, Redis, PostgreSQL
├─ frontend/                 React + TypeScript + Vite (Motion, Howler, Zustand, Tailwind)
├─ packs/sample/             오리지널 샘플 카드팩 23장
├─ tools/gen_sfx.py          효과음 합성 스크립트 (외부 음원 없음)
├─ docs/                     PRD, 컨텍스트 문서
└─ docker-compose.yml        PostgreSQL + Redis
```

## 준비물

- JDK 21
- Node.js 20.19 이상 또는 22.12 이상
- Docker (PostgreSQL, Redis 실행용)

## 실행

```bash
# 1. DB와 Redis 켜기
docker compose up -d

# 2. 규칙 엔진 테스트 (서버 없이 규칙만 검증)
cd backend
./gradlew :engine:test

# 3. 서버 실행 (http://localhost:8080). 시작할 때 packs/sample 을 자동으로 DB에 넣는다
APP_ACCESS_CODE=my-access-code ./gradlew :server:bootRun

# 4. 프론트 실행 (다른 터미널)
cd frontend
npm install
npm run dev
```

브라우저에서 http://localhost:5173 을 연다. 방장은 "방 만들기"에서 3번의 접근 코드를 입력하고,
나온 초대 링크로 다른 플레이어가 참가한다.
혼자 여러 명으로 테스트할 때는 세션이 origin(포트)별로 저장되므로 `npm run dev -- --port 5174` 처럼 포트를 달리해 하나 더 띄운다.

**서버 없이 화면만 보고 싶으면** http://localhost:5173/?demo — 봇 3명과 카드 애니메이션·효과음을 확인할 수 있다.
(미리보기의 규칙은 흉내일 뿐이고, 실제 판정은 전부 서버 엔진이 한다.)

### 같은 네트워크의 다른 기기에서 접속하기

```bash
npm run dev -- --host        # 프론트를 LAN에 공개
```

`backend/server/src/main/resources/application.yml` 의 `app.allowed-origins` 에
`http://내-PC-IP:[*]` 을 추가하고 서버를 다시 켠다. 외부에서 접속하려면 배포가 필요하다 (Phase 3).

## 구조 한눈에 보기

```
[브라우저] ──STOMP(WebSocket)──▶ [Spring 서버] ──▶ GameEngine (순수 Java)
                                     │
                          ┌──────────┴──────────┐
                       [Redis]               [PostgreSQL]
             게임 상태·세션·턴 타이머          카드팩·게임 기록
```

- **모든 판정은 서버**가 한다. 브라우저는 "이 카드 낼게요"만 보내고, 결과 이벤트를 받아 그린다.
- 게임 상태는 Redis에 JSON으로 저장하고, **버전이 같을 때만 저장(CAS)**해서 동시 요청이 꼬이지 않게 한다.
- 턴 마감 시각은 Redis ZSET에 넣고 0.5초마다 확인한다 → 서버를 재시작해도 타이머가 유지된다.

## 카드팩

```
packs/sample/
  pack.json          {"code": "sample", "name": ..., "version": 1, "visibility": "PUBLIC", "ruleMode": "SC1"}
  cards/*.json       카드 배열 (파일 이름 순서로 읽음)
```

- 카드 형식은 PRD 8장. 카드 ID는 `팩코드.이름` (예: `sample.jab`)
- 서버 시작 시 `app.packs.auto-import-dirs` 의 폴더를 검사해서 DB에 넣는다. 오류가 있으면 **어느 카드의 어느 필드인지** 로그에 나오고 그 팩은 통째로 거부된다
- 카드를 고쳤으면 `pack.json` 의 `version` 을 올린다 (같은 버전은 다시 넣지 않음)
- 관리자 API로 넣을 수도 있다: `POST /api/admin/packs` (헤더 `X-Admin-Key`, 본문 `{"pack": {...}, "cards": [...]}`)

### 비공개 카드팩

원작 카드 데이터는 **이 저장소에 커밋하지 않는다.** 별도 private 저장소에 같은 형식으로 만들고,
`pack.json` 의 `visibility` 를 `PRIVATE` 로 둔 뒤 그 폴더 경로를 `auto-import-dirs` 에 추가한다.
PRIVATE 팩은 서버 접근 코드를 아는 사람만 선택할 수 있다. 원작 이미지·사운드는 쓰지 않는다.

## 효과 추가하기 (Phase 2)

1. `engine/.../effect/` 에 `EffectHandler` 구현 클래스를 만든다 (`type()`, `apply()`, `validate()`)
2. `EffectRegistry.defaults()` 에 등록한다
3. `engine/src/test` 에 규칙 테스트를 추가하고 `./gradlew :engine:test`

등록하지 않은 효과 타입을 쓴 카드팩은 import 단계에서 거부되므로, 엔진이 모르는 카드가 게임에 들어갈 일은 없다.

## 현재 상태

| 항목 | 상태 |
|---|---|
| 규칙 엔진 (누적 판정 D7·D8, 정산, 탈락, 승리, 시간 초과, 드로우, 카드팩 검증) | ✅ 테스트 56개 통과, 무작위 수백 판 시뮬레이션 통과 |
| Phase 1 효과: 누적 증감·초기화·전달, 피해, 회복 | ✅ |
| 서버: 세션, 방, STOMP, Redis CAS 저장, 턴 타이머, 카드팩 자동 import | ✅ 빌드·기동 확인, 2인 한 판 완주 확인 |
| 프론트: 첫 화면, 대기실, 게임 화면, 애니메이션, 효과음, 미리보기 모드 | ✅ 빌드·린트 통과, 실제 서버 연동 확인 |
| 게임 기록 DB 저장, 자리 비움 강퇴, seq 기반 재전송, 서버 여러 대 | ⏳ Phase 2~3 |
