# PRD — 랜덤카드배틀 웹

| 항목 | 내용 |
|---|---|
| 문서 버전 | v0.4 (초안) — Spring Boot 4.1로 변경 (D1), Phase 1 구현 반영 (D10~D12) |
| 작성일 | 2026-09-29 |
| 상태 | Draft — 방향 검토 중 |
| 관련 문서 | docs/context.md (원작 조사, 엔진 설계 개요) |

> **이 초안의 가정** (확정 필요, 16장 참고)
> * 규칙 기본값은 **스타1 EUD(SC1) 규칙**. 스타2 규칙은 Phase 4에서 방 옵션(룰 모드)으로 추가 검토
> * 인원은 **2~6인** (원작 2~4인, 5~6인은 확장)
> * 계정 없이 **게스트 닉네임 + 초대 링크**로 입장
> * 카드는 **텍스트 카드** (원작 이미지·사운드 미사용)

---

## 1. 배경 및 문제 정의

* 스타크래프트 유즈맵 '랜덤카드배틀'은 누적 데미지를 떠넘기는 밈 카드 배틀로 인기가 많았지만, 스타1 리마스터 이후 원작 EUD 맵은 사실상 플레이할 수 없게 됐다.
* 스타2 버전이 있지만 스타2 설치·배틀넷 접속이 필요해서 가볍게 한 판 하기엔 진입 장벽이 높다.
* **해결하려는 것**: 설치 없이 브라우저에서 링크 하나로 여러 명이 원작 규칙 그대로 한 판 할 수 있게 한다.
* **엔지니어링 목표**: 카드 내용을 DB 데이터로 분리해, 엔진 코드 수정 없이 카드팩을 교체할 수 있는 구조를 만든다.

---

## 2. 목표와 비목표

### 2.1 목표

| ID | 목표 |
|---|---|
| G1 | 원작(SC1) 규칙을 재현해 원작 경험자가 "그 게임 맞다"고 느끼게 한다 |
| G2 | 초대 링크만으로 30초 안에 게임에 들어갈 수 있다 |
| G3 | 새로고침·일시 단선에도 게임이 깨지지 않는다 |
| G4 | 카드 추가·수정은 데이터(JSON → DB)만으로 가능하다 |
| G5 | 모든 판정은 서버에서 하고, 클라이언트는 입력만 보낸다 (치트 불가) |

### 2.2 비목표 (이번 범위에서 하지 않음)

* 공개 매치메이킹, 랭킹, 전적 시스템
* 결제·배틀패스·스킨 등 BM
* 모바일 앱 (모바일 브라우저 대응은 Phase 3)
* 원작 카드 이미지, 스타크래프트 리소스, 원작 BGM 사용
* AI 봇 플레이어 (테스트용 봇은 내부 도구로만 검토)
* 관전 모드

---

## 3. 성공 지표

| 지표 | 목표치 | 측정 |
|---|---|---|
| 한 판 완주율 | 플레이테스트에서 시작한 게임의 95% 이상이 정상 종료 | game_record의 종료 상태 |
| 게임 중 서버 오류 | 판당 0건 | 에러 로그 |
| 행동 처리 지연 | 서버 내부 p95 ≤ 50ms, 체감(이벤트 도달) p95 ≤ 200ms | 서버 메트릭 |
| 재접속 복구 | 60초 내 복귀 시 100% 같은 좌석·상태로 복구 | 재접속 이벤트 로그 |
| 원작 카드 커버리지 | Phase 2 종료 시 원작 팩의 90% 이상을 프리미티브만으로 구현 | 팩 검증 리포트 |
| 입장 시간 | 링크 클릭 → 대기실 착석 30초 이내 | 수동 측정 |

---

## 4. 사용자와 시나리오

### 4.1 사용자

| 사용자 | 설명 | 핵심 니즈 |
|---|---|---|
| 방장 | 서버 접근 코드로 방을 만드는 사람 | 방 설정, 플레이어 초대, 문제 있는 플레이어 강퇴 |
| 원작 경험자 | 스타에서 랜카배를 해본 플레이어 | 원작과 같은 규칙·카드 효과, 빠른 템포 |
| 처음 하는 플레이어 | 원작을 모르는 플레이어 | 카드 효과를 바로 읽을 수 있음, 지금 무슨 일이 일어났는지 로그로 이해 |

### 4.2 핵심 시나리오

1. 방장이 서버 접근 코드를 입력하고 방을 만든다 (인원, 룰 모드, 카드팩, 시작 체력 등 설정)
2. 초대 링크를 공유한다
3. 참가자들이 링크를 열고 닉네임만 입력해 대기실에 들어온다
4. 전원이 준비를 누르면 방장이 시작한다
5. 좌석이 무작위로 정해지고, 각자 손패 5장을 받는다
6. 차례대로 카드를 내거나 버리며 누적 데미지를 넘긴다
7. 한 명만 남으면 결과 화면이 나오고, 같은 멤버로 바로 다시 할 수 있다

### 4.3 예외 시나리오

* 게임 중 새로고침 → 같은 좌석으로 즉시 복귀, 손패와 필드 상태 복원
* 와이파이 끊김 → 60초 동안 좌석 유지, 그 사이 차례가 오면 턴 타이머대로 진행
* 차례에 아무것도 안 함 → 제한 시간이 지나면 무작위 카드 1장 자동 버리기
* 3회 연속 시간 초과 → "자리 비움" 표시, 방장이 강퇴 가능

---

## 5. 범위와 우선순위

* **P0**: 없으면 한 판도 못 함 (Phase 1)
* **P1**: 원작 경험을 완성하는 데 필요 (Phase 2~3)
* **P2**: 있으면 좋음 (Phase 3 이후)

| 영역 | P0 | P1 | P2 |
|---|---|---|---|
| 입장 | 게스트 닉네임, 초대 링크, 서버 접근 코드 | 닉네임 중복 처리, 프로필 색상 | 로그인 계정 |
| 방 | 생성, 참가, 준비, 시작, 퇴장 | 설정 변경, 강퇴, 다시하기 | 채팅 이모트 |
| 게임 규칙 | 제출/버리기, 누적 판정, 정산, 탈락, 승리 | 저주, 필드 락, 지속 효과, 추가 제출, 확률, 흐름 조작 | SC2 룰 모드 |
| 카드 | 공격, 데미지 감소, 데미지 전달 | 이득, 저주, 기타 전 카테고리 | 커스텀 핸들러 카드 |
| UI | 필드·좌석·손패·타이머 표시, 제출 불가 사유 표시 | 게임 로그, 카드 상세 툴팁, 대상 지정 UX, 카드 애니메이션·효과음 | 카드별 전용 연출 |
| 안정성 | 새로고침 시 스냅샷 복구 | 델타 재동기화, 지수 백오프 재접속 | 리플레이 뷰어 |
| 운영 | 카드팩 JSON import, 스키마 검증 | 팩 버전 관리, 커버리지 리포트 | 관리자 웹 화면 |

---

## 6. 기능 요구사항

> 형식: `ID` · 우선순위 · 요구사항 → **완료 기준**

### 6.1 입장·세션

* `FR-AUTH-01` · P0 · 닉네임(2~12자)만으로 게스트 세션을 발급한다
  → 토큰이 발급되고, 토큰으로 WebSocket 연결이 인증된다
* `FR-AUTH-02` · P0 · 방 생성은 서버 접근 코드를 아는 사용자만 할 수 있다 (PRIVATE 팩 보호)
  → 코드 없이 방 생성 요청 시 403. 5회 연속 실패 시 10분 차단
* `FR-AUTH-03` · P0 · 세션 토큰은 브라우저에 저장되어 새로고침 후에도 같은 플레이어로 인식된다
  → 새로고침 후 같은 좌석으로 복귀

### 6.2 방(로비)

* `FR-ROOM-01` · P0 · 방장은 방을 만들 때 다음을 설정한다

| 설정 | 기본값 | 범위 |
|---|---|---|
| 최대 인원 | 4 | 2~6 |
| 룰 모드 | SC1 | SC1 (P0), SC2 (P2) |
| 카드팩 | 원작 팩 | 등록된 팩 |
| 시작 체력 | 200 | 100~1000 |
| 체력 상한 | 500 | 시작 체력 이상 |
| 손패 수 | 5 | 4~7 |
| 턴 제한 시간 | 25초 | 15 / 25 / 40초 |

* `FR-ROOM-02` · P0 · 방 생성 시 초대 코드(6자리)와 초대 링크가 발급된다
  → 링크로 접속하면 닉네임 입력 후 바로 대기실 입장
* `FR-ROOM-03` · P0 · 참가자는 준비/준비 해제를 할 수 있고, 방장은 전원 준비 시 시작할 수 있다
  → 최소 2인, 전원 준비 전엔 시작 버튼 비활성
* `FR-ROOM-04` · P0 · 대기실에서 나가면 좌석이 비고, 방장이 나가면 가장 먼저 들어온 참가자가 방장이 된다
* `FR-ROOM-05` · P1 · 방장은 대기실에서 설정을 바꾸고 참가자를 강퇴할 수 있다
  → 설정이 바뀌면 전원의 준비 상태가 해제된다
* `FR-ROOM-06` · P1 · 게임이 끝나면 같은 방·같은 멤버로 대기실로 돌아가 다시 시작할 수 있다
* `FR-ROOM-07` · P0 · 방 목록은 제공하지 않는다 (비공개 운영)

### 6.3 게임 진행

* `FR-GAME-01` · P0 · 게임 시작 시 좌석 순서를 무작위로 정하고, 각자 손패 수만큼 카드를 받는다
* `FR-GAME-02` · P0 · 자기 차례에 카드 1장을 **내거나** 1장을 **버려야** 한다 (7.3, 7.4)
* `FR-GAME-03` · P0 · 대상이 필요한 카드는 제출 시 대상을 함께 지정한다
  → 지정 불가 대상(탈락자, 보호 상태 등)은 선택할 수 없다
* `FR-GAME-04` · P0 · 서버는 제출을 검증하고 거절 시 사유 코드를 돌려준다 (9.5)
* `FR-GAME-05` · P0 · 누적 판정과 턴 종료 정산은 7장 규칙을 따른다
* `FR-GAME-06` · P0 · 턴 제한 시간이 지나면 무작위 카드 1장을 자동으로 버린다 (버리기로 취급)
* `FR-GAME-07` · P1 · 3회 연속 시간 초과 시 해당 플레이어를 "자리 비움"으로 표시하고, 방장이 강퇴할 수 있다. 강퇴된 플레이어는 탈락 처리
* `FR-GAME-08` · P0 · 체력이 0 이하가 되면 탈락, 한 명만 남으면 승리, 같은 정산에서 남은 전원이 탈락하면 무승부
* `FR-GAME-09` · P1 · 추가 제출("한 장 더 낼 수 있음") 중에는 같은 플레이어가 조건에 맞는 카드를 이어서 내거나, 낼 카드가 없으면 1장을 버린다
* `FR-GAME-10` · P1 · 턴이 넘어갈 때 최대 2초의 전환 연출이 있고, 다음 플레이어는 이 동안 카드를 **고를 수는 있지만 제출은 할 수 없다** (원작의 대기 텀 재현)

### 6.4 카드

* `FR-CARD-01` · P0 · 카드는 카드팩 단위로 DB에서 불러오며, 드로우는 카드별 가중치(weight)에 따른 무작위 추첨이다
* `FR-CARD-02` · P0 · 손패의 각 카드에 **지금 낼 수 있는지**와 낼 수 없는 **이유**(필드 락 / 저주 / 조건 미충족)를 표시한다
* `FR-CARD-03` · P1 · 카드를 누르거나 길게 누르면 상세 설명(공격력, 태그, 효과 문구)을 보여준다 (원작의 Insert 키 대응)
* `FR-CARD-04` · P0 · 카드 효과는 이펙트 프리미티브의 조합으로 실행된다 (8장)
* `FR-CARD-05` · P2 · 프리미티브로 표현이 안 되는 카드는 `CUSTOM` 핸들러로 구현하되, 팩 전체의 10% 이하로 유지한다

### 6.5 화면 표시 (HUD)

* `FR-UI-01` · P0 · 화면 중앙에 **현재 공격력**과 **누적 데미지**를 크게 표시하고, 필드에 놓인 카드를 보여준다
* `FR-UI-02` · P0 · 각 좌석에 닉네임, 체력(바 + 숫자), 손패 수, 저주 아이콘, 상태 아이콘(남은 턴), 연결 상태를 표시한다
* `FR-UI-03` · P0 · 현재 차례, 턴 진행 방향, 남은 시간을 표시한다
* `FR-UI-04` · P1 · 게임 로그 패널에 모든 행동을 한 줄씩 남긴다
  → 예: "민수가 [카드명]을 냈다 · 누적 45 → 지훈 차례"
* `FR-UI-05` · P1 · 대상 지정 카드를 고르면 지정 가능한 좌석만 강조되고, 좌석을 눌러 확정한다
* `FR-UI-06` · P1 · 손패 공개 저주가 걸린 플레이어의 손패는 전원에게 앞면으로 보인다
* `FR-UI-07` · P0 · 결과 화면에 순위(탈락 역순)와 다시하기 버튼을 표시한다
* `FR-UI-08` · P1 · 카드 제출, 피해, 회복, 저주, 탈락 시 애니메이션과 효과음을 재생한다
  → 음소거·볼륨 조절 가능. 효과음은 CC0 등 상업 이용 가능한 무료 음원 또는 직접 제작한 것만 사용
* `FR-UI-09` · P1 · 서버 이벤트는 받은 순서대로 연출 큐에 넣어 차례로 재생한다
  → 연출이 밀려도 화면의 숫자(체력·누적)는 서버 상태 기준으로 맞춰지고, 큐가 길어지면 연출을 빠르게 넘긴다

### 6.6 연결·동기화

* `FR-SYNC-01` · P0 · 재접속 시 서버는 해당 플레이어 시점의 전체 스냅샷을 보낸다
* `FR-SYNC-02` · P1 · 마지막으로 받은 이벤트 번호(seq)를 보내면 놓친 이벤트만 다시 보낸다. 너무 많이 놓쳤으면 스냅샷으로 대체
* `FR-SYNC-03` · P1 · 연결이 끊기면 클라이언트는 지수 백오프(+지터)로 재접속한다 (1s → 2s → 4s … 최대 30s)
* `FR-SYNC-04` · P0 · 단선된 플레이어의 좌석은 60초간 유지되고, 그 사이에도 턴 타이머는 정상 동작한다

### 6.7 운영

* `FR-OPS-01` · P0 · 카드팩 JSON을 불러와 DB에 넣는 import 명령(또는 관리자 API)을 제공한다
* `FR-OPS-02` · P0 · import 시 형식 검사(PackParser)와 의미 검사(PackValidator)를 하고, 모르는 필드·이펙트 타입·잘못된 값이 하나라도 있으면 전체를 거부한다
* `FR-OPS-03` · P1 · 팩은 버전을 가지며, 진행 중인 게임은 시작 시점의 팩 버전으로 끝까지 진행한다
* `FR-OPS-04` · P1 · 팩별로 프리미티브만으로 구현된 카드 / CUSTOM 카드 / 미구현 카드 비율 리포트를 출력한다

---

## 7. 게임 규칙 명세 (SC1 모드)

### 7.1 용어

| 용어 | 정의 |
|---|---|
| 현재 공격력 (A) | 체인에서 직전에 나온 공격 카드의 공격력. 체인이 없으면 0 |
| 누적 데미지 (D) | 현재 체인에 쌓인 데미지 합 |
| 체인 | 공격 카드가 이어지며 D가 쌓이는 흐름. 누군가 D를 받거나 전달·초기화되면 끝남 |
| 필드 | 직전에 **제출된** 카드(추가 제출 포함)가 놓이는 곳. 필드 락 효과의 근거 |
| 수령 | 턴 종료 시 D만큼 체력이 깎이는 것 |
| 정산 | 턴 종료 시 수령·저주·지속 효과·확률 효과를 순서대로 처리하는 단계 |

### 7.2 게임 상태 흐름

```
LOBBY → STARTING → TURN_ACTIVE ⇄ EXTRA_PLAY
                        ↓
                   SETTLEMENT → CHECK_END → TURN_TRANSITION → TURN_ACTIVE (다음 플레이어)
                                    ↓
                                 FINISHED
```

* `TURN_ACTIVE`: 현재 플레이어의 제출/버리기 대기 (타이머 작동)
* `EXTRA_PLAY`: 추가 제출 효과로 같은 플레이어가 한 장 더 내는 중 (타이머 연장 없음)
* `SETTLEMENT`: 7.6 순서대로 정산
* `CHECK_END`: 탈락·승리·무승부 판정
* `TURN_TRANSITION`: 다음 플레이어 결정, 전환 연출

### 7.3 제출 가능 판정 순서

1. 자기 차례인가 (아니면 `NOT_YOUR_TURN`)
2. 손패에 있는 카드인가 (아니면 `CARD_NOT_IN_HAND`)
3. `alwaysPlayable` 카드면 → 4·5를 건너뜀
4. 필드 락에 걸리는가 (걸리면 `CARD_NOT_PLAYABLE / FIELD_LOCK`)
5. 자신의 저주 락에 걸리는가 (걸리면 `CARD_NOT_PLAYABLE / CURSE_LOCK`)
6. 카드의 제출 조건(conditions)을 만족하는가 (아니면 `CARD_NOT_PLAYABLE / CONDITION_UNMET`)
7. 대상이 필요한 카드면 대상이 유효한가 (아니면 `INVALID_TARGET`)

> 버리기는 항상 가능하다 (손패가 0장인 경우는 드로우 규칙상 발생하지 않음)

### 7.4 누적 판정 (제출 직후)

| 행동 | 조건 | 결과 |
|---|---|---|
| 공격 카드 제출 (공격력 p) | A = 0 또는 p ≥ A | D += p, A = p. 수령 없음, 체인 계속 |
| 공격 카드 제출 (공격력 p) | p < A | **수령 예약**(현재 D). 정산 후 새 체인 시작: A = p, D = p |
| 데미지 감소 카드 | — | 효과로 D 감소 → 남은 D **수령 예약**, 정산 후 체인 종료 (A = 0, D = 0) |
| 데미지 전달 카드 (다음 차례로) | — | 수령 없음. A·D(효과에 따라 배수)를 유지한 채 체인이 다음 플레이어로 |
| 데미지 전달 카드 (즉시 대상에게) | — | 대상이 즉시 D만큼 피해. 체인 종료 (A = 0, D = 0) |
| 이득·저주·기타 카드 | D > 0 | 효과 적용 후 **수령 예약**, 정산 후 체인 종료 |
| 이득·저주·기타 카드 | D = 0 | 효과만 적용 |
| 버리기 | D > 0 | **수령 예약**, 정산 후 체인 종료 |
| 버리기 | D = 0 | 아무 일도 없음 |

* 카드 효과로 "이번 턴 누적 데미지를 받지 않음"이 걸리면 수령 예약을 취소한다
* 새 체인이 시작될 때 카드의 "받았을 때 발동" 효과(예: 다음 체인에 D +N)가 적용된다
* 현재 공격력과 **같은** 공격 카드는 체인을 이어간다 (D7)
* 낮은 공격 카드로 수령한 경우, 그 카드로 새 체인이 시작된다: A = p, D = p (D8)

### 7.5 SC2 모드 차이 (P2)

* p ≤ A인 공격 카드 → (D − A)만 수령
* 반동 데미지 없음
* `special` 카드: 모든 락 무시 + 제출 후 "더 높은 공격 카드만 낼 수 있음" 락
* 체력 일정 이하에서 1회 역전 드로우 스킬

### 7.6 턴 종료 정산 순서

1. **누적 수령**: 수령 예약이 있으면 D만큼 체력 감소. 이어서 7.4에 따라 체인 종료(A = 0, D = 0) 또는 새 체인 시작(A = p, D = p)
2. **예약 효과**: 이번에 낸 카드의 `ON_RECEIVE`·`ON_TURN_END` 효과 실행, `ON_NEXT_TURN` 효과 등록
3. **저주 발동**: 현재 플레이어에게 걸린 저주의 턴 종료 효과
4. **지속 상태 틱**: 현재 플레이어의 상태 효과 발동 후 남은 턴 −1, 0이면 제거
5. **확률 효과**: 시한폭탄류 판정 (항상 맨 마지막)
6. **체력 보정**: 체력 상한 초과분 제거
7. **탈락 판정**: 체력 0 이하 플레이어 탈락 (7.8)
8. **드로우**: 현재 플레이어 손패를 한도까지 보충
9. **필드 정리**: 7.7 규칙에 따라 만료된 필드 카드 제거

> 같은 단계 안의 효과끼리의 순서는 **카드를 낸 순서 → 좌석 순서**로 처리한다

### 7.7 필드 규칙

* 카드를 **제출**하면 필드가 그 카드(추가 제출이 있으면 그 카드들 전부)로 **교체**된다
* **버리기는 필드를 바꾸지 않는다**
* 필드 락 효과는 **최대 2턴** 지속 후 자동 해제된다 (원작 SC1에서 락이 끝없이 이어지던 문제를 SC2 방식으로 보완)

### 7.8 저주·탈락 규칙

* 한 플레이어에게 저주는 최대 1개, 새 저주는 기존 저주를 **덮어쓴다**
* 저주는 자연 해제되지 않으며 해제 효과로만 풀린다
* 저주 효과는 **저주받은 플레이어의 턴 종료 시** 발동한다
* 탈락한 플레이어가 건 저주는 모두 해제된다 (SC1 규칙)
* 탈락한 플레이어의 손패는 버려지고, 턴 순서에서 제외된다
* 탈락은 정산 7단계에서 한 번에 판정한다 (정산 도중 체력이 0 이하로 내려갔다가 회복되면 생존)

### 7.9 드로우 규칙

* 게임 시작 시와 매 턴 정산 후, 손패를 한도까지 채운다
* 추첨은 팩 전체에서 카드별 weight 비율로 뽑는다 (같은 카드가 여러 장 나올 수 있음)
* 손패 한도는 효과로 증감 가능 (최소 1)

### 7.10 게임 종료

* 생존자가 1명 → 승리
* 같은 정산에서 생존자 전원 탈락 → 무승부
* 무승부 카운트다운 효과가 0이 되면 → 무승부
* 결과 순위는 탈락 역순, 같은 정산 탈락자는 공동 순위

---

## 8. 카드 데이터 스펙

> 외부 룰 엔진은 쓰지 않는다. 제출 조건과 효과를 카드 JSON에 선언하고, 서버가 Java 핸들러로 해석한다.
> 아래 예시 카드는 **설명용 샘플**이며 원작 카드가 아니다.

### 8.1 카드 스키마

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | string | O | 팩 내 고유 ID (`pack.card` 형식) |
| `name` | string | O | 카드 이름 |
| `category` | enum | O | `ATTACK`, `SUPPORT`, `BENEFIT`, `CURSE`, `MISC` |
| `subcategory` | string | | 원작 분류 (예: `REDUCE`, `TRANSFER`, `HEAL`) |
| `attack` | int | 공격 카드 | 고정 공격력 |
| `attackRange` | [min, max] | | 랜덤 공격력 (있으면 `attack` 대신 사용) |
| `tags` | string[] | | 속성·종족 태그 (예: `FIRE`, `ELECTRIC`) |
| `targeting` | enum | | 대상 지정 방식 (8.4) |
| `alwaysPlayable` | bool | | 필드 락·저주 락 무시 |
| `conditions` | Condition[] | | 제출 조건 (8.2) |
| `effects` | Effect[] | | 효과 목록 (8.3) |
| `description` | string | O | 카드에 표시할 효과 문구 |
| `flavor` | string | | 부가 문구 |
| `weight` | int | O | 드로우 가중치 (0이면 드로우되지 않고 효과로만 생성) |

### 8.2 제출 조건 (conditions)

* 배열 안의 조건은 **모두 만족(AND)** 해야 한다
* OR가 필요하면 `{ "type": "ANY", "of": [ ... ] }`로 묶는다

| type | 파라미터 | 의미 |
|---|---|---|
| `SELF_HP_LTE` / `SELF_HP_GTE` | value | 내 체력 ≤ / ≥ value |
| `ACCUMULATED_GTE` | value | 누적 데미지 ≥ value |
| `ACCUMULATED_ZERO` | — | 누적 데미지가 0 |
| `FIELD_HAS_TAG` | tag | 필드에 해당 태그 카드가 있음 |
| `FIELD_HAS_CARD` | cardId | 필드에 해당 카드가 있음 |
| `SELF_CURSED` | — | 나에게 저주가 걸려 있음 |
| `HAND_HAS` | filter, count | 내 손패에 조건에 맞는 카드가 count장 이상 |
| `ANY` | of | 하위 조건 중 하나라도 만족 |
| `NOT` | condition | 하위 조건 부정 |

### 8.3 효과 (effects)

공통 필드: `type`, `timing`(기본 `ON_PLAY`), 그리고 타입별 파라미터

**timing**

| 값 | 발동 시점 |
|---|---|
| `ON_PLAY` | 제출 즉시 |
| `ON_RECEIVE` | 이 카드를 낸 턴에 누적 데미지를 수령할 때 |
| `ON_TURN_END` | 이 카드를 낸 턴의 정산 시 |
| `ON_NEXT_TURN` | 이 카드를 낸 사람의 다음 차례 시작 시 |

**프리미티브 목록**

| 그룹 | type | 주요 파라미터 | 우선순위 |
|---|---|---|---|
| 누적 | `MODIFY_ACCUMULATED` | op(`ADD`/`SUB`/`MUL`/`PERCENT_SUB`), value | P0 |
| 누적 | `RESET_ACCUMULATED` | — | P0 |
| 누적 | `TRANSFER_ACCUMULATED` | to, multiplier, immediate | P0 |
| 누적 | `SWAP_ATTACK_AND_ACCUMULATED` | — | P1 |
| 누적 | `ABSORB_ACCUMULATED` | as(`HEAL`), ratio | P1 |
| 누적 | `IMMUNE_THIS_TURN` | — | P1 |
| 체력 | `DAMAGE` / `HEAL` | target, amount | P0 |
| 체력 | `DRAIN` | target, amount | P1 |
| 체력 | `SET_HP` / `SWAP_HP` / `EQUALIZE_HP` / `HALVE_HP` / `SET_HP_CAP` | target, value | P1 |
| 저주·상태 | `APPLY_CURSE` | target, curse(CurseDef) | P1 |
| 저주·상태 | `REMOVE_CURSE` / `REFLECT_CURSE` | scope | P1 |
| 저주·상태 | `APPLY_STATUS` | target, status, turns, params | P1 |
| 저주·상태 | `DISPEL_STATUSES` | target, filter | P1 |
| 필드 | `FIELD_LOCK` | filter, turns(최대 2) | P1 |
| 공격 보정 | `CONDITIONAL_ATTACK` | condition, set 또는 add | P1 |
| 제출 | `EXTRA_PLAY` | filter, count, attackMode(`SUM`/`DOUBLE`) | P1 |
| 제출 | `PLAY_ALL` | filter | P1 |
| 손패 | `DRAW` / `REPLACE_HAND` / `SWAP_HAND` / `HAND_LIMIT` / `REVEAL_HAND` / `RETRIEVE_FROM_FIELD` / `GIVE_CARDS` | target, count 등 | P1 |
| 흐름 | `SKIP_NEXT` / `REVERSE_ORDER` / `LOCK_NEXT_PLAYER` / `SET_TURN_TIME` / `START_DRAW_COUNTDOWN` | — | P1 |
| 확률 | `CHANCE` | p, then[], else[] | P1 |
| 확률 | `RANDOM_CHOICE` | table[{weight, effects[]}] | P1 |
| 확률 | `TIME_BOMB` | p, damage, pByTag | P1 |
| 기타 | `CUSTOM` | handler | P2 |

**CurseDef (저주 정의)**

```json
{
  "id": "curse.sample_monday",
  "onTurnEnd": [ { "type": "DAMAGE", "target": "CURSED", "amount": 5 } ],
  "locks": [ { "category": "SUPPORT" } ]
}
```

* `onTurnEnd`: 저주받은 사람의 턴 종료 시 실행할 효과
* `locks`: 저주받은 사람이 낼 수 없는 카드 필터

**카드 필터 (filter)**: `{ category, subcategory, tags, attackGte, attackLte, hasEffects, cardIds }` — 지정한 필드는 모두 만족(AND)

### 8.4 대상 지정 (targeting / target)

| 값 | 의미 |
|---|---|
| `NONE` | 대상 없음 |
| `SELF` | 나 |
| `CHOSEN_OTHER` / `CHOSEN_ANY` | 내가 고른 다른 사람 / 나 포함 아무나 (카드의 `targeting`에 사용) |
| `CHOSEN` | 카드의 `targeting`으로 고른 그 대상 (효과의 `target`/`to`에 사용) |
| `NEXT` / `PREV` | 다음 / 이전 차례 플레이어 |
| `ALL` / `ALL_OTHERS` | 전원 / 나 빼고 전원 |
| `RANDOM_ANY` / `RANDOM_OTHER` | 무작위 (나 포함 / 제외) |
| `TOP_HP` | 체력 1위 (동률이면 전원) |
| `CURSED` | 저주 정의 안에서 저주받은 사람 |

* 지정 불가 상태(`UNTARGETABLE`)인 플레이어는 `CHOSEN_*`에서 제외된다
* 대상 강제 저주가 걸린 사람의 `CHOSEN_*`은 자기 자신으로 바뀐다

### 8.5 샘플 카드 (설명용)

```json
[
  {
    "id": "sample.jab",
    "name": "가벼운 잽",
    "category": "ATTACK",
    "attack": 10,
    "description": "공격력 10",
    "weight": 12
  },
  {
    "id": "sample.last_stand",
    "name": "벼랑 끝 한 방",
    "category": "ATTACK",
    "attack": 40,
    "conditions": [ { "type": "SELF_HP_LTE", "value": 100 } ],
    "effects": [ { "type": "DAMAGE", "target": "SELF", "amount": 10 } ],
    "description": "체력 100 이하일 때만 사용. 사용 시 내 체력 -10",
    "weight": 4
  },
  {
    "id": "sample.not_my_problem",
    "name": "내 알 바 아님",
    "category": "SUPPORT",
    "subcategory": "TRANSFER",
    "targeting": "CHOSEN_OTHER",
    "effects": [ { "type": "TRANSFER_ACCUMULATED", "to": "CHOSEN", "multiplier": 1, "immediate": true } ],
    "description": "누적 데미지를 고른 사람에게 바로 입힌다",
    "weight": 2
  },
  {
    "id": "sample.monday",
    "name": "월요병",
    "category": "CURSE",
    "targeting": "CHOSEN_OTHER",
    "effects": [ { "type": "APPLY_CURSE", "target": "CHOSEN", "curse": {
      "id": "curse.sample_monday",
      "onTurnEnd": [ { "type": "DAMAGE", "target": "CURSED", "amount": 5 } ]
    } } ],
    "description": "대상은 자기 턴이 끝날 때마다 체력 -5",
    "weight": 3
  },
  {
    "id": "sample.read_receipt",
    "name": "읽씹",
    "category": "ATTACK",
    "attack": 15,
    "effects": [ { "type": "FIELD_LOCK", "filter": { "category": "SUPPORT" }, "turns": 1 } ],
    "description": "이 카드가 필드에 있는 동안 보조 카드를 낼 수 없다",
    "weight": 5
  }
]
```

### 8.6 팩 파일과 검증

* 팩 파일 = `pack.json` (메타데이터) + `cards/*.json`
* import 시 검증 항목
  * 형식: 필수 필드, 타입, 알 수 없는 필드(오타) 거부
  * 모르는 `type`, `timing`, `targeting` 값 거부
  * 참조하는 카드 ID(`FIELD_HAS_CARD`, `GIVE_CARDS` 등)가 팩 안에 존재하는지
  * `FIELD_LOCK.turns` ≤ 2, `weight` ≥ 0
* 검증 실패 시 팩 전체를 거부하고 오류 위치(파일·카드 ID·필드)를 출력
* **원작 팩 파일은 private 저장소에만 둔다** (공개 레포에는 샘플 팩만)

---

## 9. 통신 프로토콜

### 9.1 REST API (방 입장 전)

| 메서드 | 경로 | 설명 | 우선순위 |
|---|---|---|---|
| POST | `/api/sessions` | 게스트 세션 발급 `{nickname}` → `{token, playerId}` | P0 |
| POST | `/api/rooms` | 방 생성 (헤더 `X-Access-Code`) `{settings}` → `{roomId, inviteCode}` | P0 |
| GET | `/api/rooms/{inviteCode}` | 방 요약 조회 (인원, 상태) | P0 |
| POST | `/api/rooms/{inviteCode}/join` | 참가 → `{roomId, seat}` | P0 |
| GET | `/api/packs` | 선택 가능한 카드팩 목록 (접근 코드가 있을 때만 PRIVATE 포함) | P0 |
| POST | `/api/admin/packs` | 카드팩 import (관리자 키) | P0 |

### 9.2 WebSocket / STOMP

* 엔드포인트: `/ws`, CONNECT 헤더에 세션 토큰
* **클라이언트 → 서버 (SEND)**

| 목적지 | 페이로드 | 설명 |
|---|---|---|
| `/app/rooms/{roomId}/ready` | `{ready}` | 준비 토글 |
| `/app/rooms/{roomId}/settings` | `{settings}` | 설정 변경 (방장) |
| `/app/rooms/{roomId}/start` | — | 시작 (방장) |
| `/app/rooms/{roomId}/kick` | `{playerId}` | 강퇴 (방장) |
| `/app/games/{gameId}/play` | `{actionId, cardInstanceId, targetId?, expectedVersion}` | 카드 제출 |
| `/app/games/{gameId}/discard` | `{actionId, cardInstanceId, expectedVersion}` | 카드 버리기 |
| `/app/games/{gameId}/sync` | `{lastSeq}` | 재동기화 요청 |

* **서버 → 클라이언트 (SUBSCRIBE)**

| 목적지 | 내용 |
|---|---|
| `/topic/rooms/{roomId}` | 대기실 이벤트 (입장, 퇴장, 준비, 설정 변경, 게임 시작) |
| `/topic/games/{gameId}` | 공개 게임 이벤트 (모든 플레이어가 보는 것) |
| `/user/queue/games/{gameId}` | 개인 이벤트 (내 손패, 거절 사유, 스냅샷) |

### 9.3 이벤트 형식

```json
{
  "seq": 128,
  "version": 57,
  "type": "CARD_PLAYED",
  "at": "2026-09-29T12:00:00Z",
  "payload": { "playerId": "p2", "card": { "id": "sample.jab", "instanceId": "c-91" }, "targetId": null }
}
```

* `seq`: 게임 내 이벤트 일련번호 (재동기화 기준)
* `version`: 게임 상태 버전 (낙관적 동시성 제어 기준)

### 9.4 이벤트 타입

* **공개**: `GAME_STARTED`, `TURN_STARTED`, `CARD_PLAYED`, `CARD_DISCARDED`, `ACCUMULATION_CHANGED`, `HP_CHANGED`, `CURSE_APPLIED`, `CURSE_REMOVED`, `STATUS_CHANGED`, `FIELD_CHANGED`, `HAND_REVEALED`, `HAND_COUNT_CHANGED`, `ORDER_CHANGED`, `PLAYER_ELIMINATED`, `PLAYER_CONNECTION`, `TURN_ENDED`, `GAME_ENDED`
* **개인**: `HAND_UPDATED`, `PLAYABILITY_UPDATED`(카드별 제출 가능 여부·사유), `ACTION_REJECTED`, `SNAPSHOT`

### 9.5 거절 코드

| 코드 | 의미 |
|---|---|
| `NOT_YOUR_TURN` | 자기 차례가 아님 |
| `CARD_NOT_IN_HAND` | 손패에 없는 카드 |
| `CARD_NOT_PLAYABLE` | 낼 수 없음 (`reason`: `FIELD_LOCK` / `CURSE_LOCK` / `CONDITION_UNMET`) |
| `INVALID_TARGET` | 잘못된 대상 |
| `STALE_VERSION` | 클라이언트 상태가 오래됨 → 스냅샷 재요청 |
| `DUPLICATE_ACTION` | 이미 처리한 actionId (재전송 무시) |
| `RATE_LIMITED` | 요청 과다 |

* `actionId`(UUID)로 같은 요청의 중복 처리를 막는다 (재전송 안전)

---

## 10. 데이터 모델

### 10.1 PostgreSQL (영속 데이터)

```
card_pack      (id, code, name, visibility[PRIVATE|PUBLIC], rule_mode, version, created_at)
card           (id, pack_id, code, name, category, weight,
                definition jsonb)   -- 카드 JSON 원본 전체. 조회용 컬럼만 따로 둔다
game_record    (id, room_id, pack_id, pack_version, rule_mode, settings jsonb,
                seed, started_at, ended_at, result jsonb)
game_event_log (game_id, seq, type, payload jsonb, created_at)   -- 게임 종료 시 Redis에서 일괄 저장
```

### 10.2 Redis (진행 중 상태)

| 키 | 타입 | 내용 |
|---|---|---|
| `session:{token}` | hash | playerId, nickname, 만료 |
| `room:{roomId}` | hash | 설정, 참가자, 준비 상태, 방장 |
| `invite:{inviteCode}` | string | roomId |
| `game:{gameId}:state` | string(JSON) | 전체 게임 상태 + version |
| `game:{gameId}:events` | stream | 이벤트 (재동기화용, 게임 종료 후 PG로 이동) |
| `timers:turn` | zset | member = gameId, score = 턴 마감 시각 |
| `ratelimit:{playerId}` | zset | 슬라이딩 윈도우 |

* 상태 변경은 Lua 스크립트로 `version` 비교 후 원자적으로 저장 (CAS)
* 게임 로그는 종료 후 30일 보관

---

## 11. 비기능 요구사항

| 영역 | 요구사항 |
|---|---|
| 성능 | 행동 1건 서버 처리 p95 ≤ 50ms, 이벤트 도달 p95 ≤ 200ms |
| 규모 | Phase 1: 동시 방 10개 × 6명. Phase 4: 동시 방 500개 부하 테스트 통과 |
| 가용성 | Phase 1은 단일 인스턴스 허용. 게임 상태가 Redis에 있으므로 서버 재시작 후 진행 중 게임 이어서 진행 |
| 보안 | 모든 판정 서버 권위, 손패는 개인 큐로만 전송, 플레이어당 행동 초당 5건 제한, 접근 코드 무차별 대입 차단, 입력 검증(닉네임·설정 범위) |
| 재현성 | 게임별 seed + 입력 로그로 같은 게임을 그대로 재실행할 수 있다 |
| 관측성 | 구조화 로그(gameId, seq 포함), 행동 처리 시간·활성 방 수·WebSocket 연결 수 메트릭 |
| 브라우저 | 데스크톱 최신 Chrome / Edge / Firefox / Safari. 모바일 브라우저는 Phase 3 |
| 법적 | 원작 카드 데이터는 private 저장소·서버에만 존재, 원작 이미지·사운드·BGM 미사용 |

---

## 12. 기술 스택과 아키텍처 요약

| 영역 | 선택 |
|---|---|
| 백엔드 | Spring Boot 4.1, Java 21 (가상 스레드), Jackson 3 |
| 실시간 통신 | spring-websocket + STOMP |
| 다중 서버 전파 | 인스턴스별 SimpleBroker + Redis Pub/Sub 재전송 |
| 진행 중 상태 | Redis (JSON 상태 + Lua CAS, Stream, ZSET 타이머) |
| 영속 데이터 | PostgreSQL (카드팩, 게임 기록) |
| 규칙 실행 | Java Strategy 패턴 이펙트 핸들러 + 조건 JSON 평가기 (**외부 룰 엔진 보류**) |
| 턴 타이머 | Redis ZSET 마감 큐 + 폴링 워커 |
| 프론트엔드 | React + TypeScript + Vite, Motion(애니메이션), Howler.js(효과음), @stomp/stompjs(서버 연결), Zustand(상태), Tailwind CSS(스타일) |
| 배포 | Docker Compose (Phase 1~3), K8s는 Phase 4에서 검토 |

```
[Browser] ──WebSocket/STOMP──▶ [Spring Boot]
                                 ├─ GameService (턴 처리, 판정, 정산)
                                 ├─ EffectInterpreter (프리미티브 핸들러)
                                 ├─ ConditionEvaluator
                                 ├─ TurnTimerWorker
                                 └─ PackImporter
                                       │
                         ┌─────────────┼─────────────┐
                      [Redis]                   [PostgreSQL]
             상태·이벤트·타이머·PubSub         카드팩·게임 기록
```

---

## 13. 테스트 전략

| 종류 | 내용 | 도구 |
|---|---|---|
| 단위 | 프리미티브·조건 타입별 테스트 | JUnit 5 |
| 규칙 시나리오 | "초기 상태 + 행동 목록 → 기대 상태"를 JSON 픽스처로 작성, seed 고정 | JUnit 파라미터 테스트 |
| 원작 대조 | 원작 플레이 영상에서 뽑은 상황을 시나리오 테스트로 재현하는 체크리스트 | 시나리오 픽스처 |
| 통합 | Redis·PostgreSQL을 띄워 방 생성 → 게임 종료까지 STOMP 클라이언트로 검증 | Testcontainers |
| 동시성 | 같은 턴에 중복·지연 요청을 보내 CAS·actionId 중복 방지 확인 | 통합 테스트 |
| 부하 | 가상 플레이어로 다수 방 동시 진행 | k6 또는 Gatling (Phase 4) |
| 플레이테스트 | 4인 이상, 각 Phase 종료 시 | 설문 + 로그 확인 |

---

## 14. 마일스톤과 완료 기준

| Phase | 범위 | 완료 기준 (Definition of Done) |
|---|---|---|
| **1. MVP** | 입장·방·턴 루프·누적 판정·정산·탈락·승리, 턴 타이머, 공격/감소/전달 카드, 카드팩 import, 새로고침 스냅샷 복구, 기본 HUD | 4명이 초대 링크로 들어와 샘플 팩으로 한 판을 끝까지 완주. 서버 오류 0건 |
| **2. 원작 카드** | 저주, 필드 락, 지속 상태, 추가 제출, 태그, 확률, 흐름·손패 조작, 게임 로그, 카드 상세, 대상 지정 UX, 팩 커버리지 리포트 | 원작 팩 90% 이상 프리미티브로 구현. 원작 경험자 플레이테스트에서 "규칙이 원작과 다르다"는 지적이 치명적인 것 없음 |
| **3. 안정성·완성도** | 델타 재동기화, 백오프 재접속, 자리 비움 처리, 연출·효과음(오리지널/무료 음원), 모바일 브라우저, 리플레이 로그 | 플레이 중 강제 단선 테스트 10회 모두 복구. 모바일에서 한 판 완주 |
| **4. 공개 준비** | 오리지널 카드팩, 규칙 차별화 또는 원작자 허락, SC2 룰 모드, 매치메이킹, 부하 테스트, 모니터링 | 공개 팩만으로 한 판 완주, 동시 방 500개 부하 테스트 통과, 법률 자문 완료 |

---

## 15. 리스크와 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| 원작 규칙 세부가 문서마다 다르거나 모호함 | 원작 경험자가 이질감을 느낌 | 모호한 규칙은 16장에 모아 영상으로 확인, 필요하면 방 옵션으로 둘 다 지원 |
| 카드 효과가 프리미티브로 표현이 안 됨 | 엔진이 카드별 하드코딩으로 오염 | `CUSTOM` 핸들러로 격리하고 10% 이하로 관리, 반복되는 패턴은 프리미티브로 승격 |
| 효과 조합 버그 (추가 제출 + 전달 + 저주 등) | 게임이 멈추거나 상태가 꼬임 | 시나리오 테스트 픽스처 누적, seed 재현으로 버그 재현 |
| 동시 요청·재전송으로 상태 꼬임 | 카드 중복 제출 등 | 버전 CAS + actionId 중복 방지 |
| 원작 IP 문제 | 공개 시 분쟁 가능 | 초기에는 비공개 운영, 공개 전 원작자 허락 또는 규칙·카드 차별화 + 법률 자문 |
| 1인 개발 범위 과다 | 완성 전에 지침 | P0만으로 Phase 1을 먼저 끝내고 바로 플레이테스트 |

---

## 16. 결정 사항과 미결 사항

### 16.1 결정 사항

| ID | 결정 |
|---|---|
| D1 | 백엔드는 Spring Boot 4.1 (Java 21). 3.5는 2026-06-30에 무료 지원 종료 |
| D2 | 외부 룰 엔진(GoRules ZEN) **보류**. Java 이펙트 핸들러 + 조건 JSON |
| D3 | 원작 규칙 이식 + 카드는 DB 카드팩으로 교체 가능하게 |
| D4 | 비공개 운영 우선, 원작 이미지·사운드·BGM 미사용 |
| D5 | 필드 락은 최대 2턴 (SC1 무한 락 문제 보완) |
| D6 | 정산 순서는 7.6을 따르고, 확률 효과는 항상 마지막 |
| D7 | 현재 공격력과 같은 공격 카드를 내면 누적 데미지를 쌓고 체인을 이어간다 |
| D8 | 낮은 공격 카드를 내서 누적 데미지를 받으면, 그 카드로 새 체인이 시작된다 (A = p, D = p) |
| D9 | 프론트엔드는 React + TypeScript + Vite. 애니메이션은 Motion, 효과음은 Howler.js |
| D10 | Phase 1은 서버 1대 기준: 내장 SimpleBroker + JVM 락. Redis Pub/Sub 재전송은 서버를 늘릴 때 추가 |
| D11 | 규칙 엔진은 Spring·JSON 라이브러리에 의존하지 않는 별도 모듈(engine). 서버는 JSON → Map 변환만 한다 |
| D12 | 효과·조건은 직전 필드를 보고, 필드 교체는 누적 판정 뒤에 한다 (7.7) |

### 16.2 미결 사항

| ID | 질문 | 현재 가정 |
|---|---|---|
| O1 | 기준 규칙을 SC1으로 확정할지, SC2 모드를 언제 넣을지 | SC1 기본, SC2는 Phase 4 |
| O2 | 5~6인 허용 시 밸런스 (원작은 4인 기준) | 허용하되 플레이테스트로 확인 |
| O3 | 원작 카드풀 기준 버전 (1.14 / 1.19.1 / SC2) | 미정 |
| O7 | 원작자(GRAYD) 연락 시점 | 미정 |
| O8 | 게임 이름 | 미정 |
| O9 | 게스트 방식 유지 vs 로그인 도입 | 게스트 유지 |
| O10 | 턴 제한 시간 기본값 (SC1 원작 값 불명) | 25초 |
