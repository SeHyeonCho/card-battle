-- 카드팩 (PRD 10.1)
CREATE TABLE card_pack (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    visibility  VARCHAR(16)  NOT NULL,
    rule_mode   VARCHAR(16)  NOT NULL,
    version     INTEGER      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_card_pack_code_version UNIQUE (code, version)
);

-- 카드. 카드 정의 전체(효과·조건 포함)는 definition에 원본 JSON 그대로 저장한다
CREATE TABLE card (
    id          BIGSERIAL    PRIMARY KEY,
    pack_id     BIGINT       NOT NULL REFERENCES card_pack (id) ON DELETE CASCADE,
    code        VARCHAR(128) NOT NULL,
    name        VARCHAR(128) NOT NULL,
    category    VARCHAR(16)  NOT NULL,
    weight      INTEGER      NOT NULL,
    definition  JSONB        NOT NULL,
    CONSTRAINT uq_card_pack_code UNIQUE (pack_id, code)
);

-- 끝난 게임 기록 (Phase 3에서 사용)
CREATE TABLE game_record (
    id            VARCHAR(32)  PRIMARY KEY,
    room_id       VARCHAR(32)  NOT NULL,
    pack_code     VARCHAR(64)  NOT NULL,
    pack_version  INTEGER      NOT NULL,
    rule_mode     VARCHAR(16)  NOT NULL,
    settings      JSONB        NOT NULL,
    seed          BIGINT       NOT NULL,
    started_at    TIMESTAMPTZ  NOT NULL,
    ended_at      TIMESTAMPTZ,
    result        JSONB
);

CREATE TABLE game_event_log (
    game_id     VARCHAR(32)  NOT NULL REFERENCES game_record (id) ON DELETE CASCADE,
    seq         BIGINT       NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    payload     JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (game_id, seq)
);
