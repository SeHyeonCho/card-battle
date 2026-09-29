package com.cardbattle.engine;

import com.cardbattle.engine.card.CardCategory;
import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.card.Targeting;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.pack.PackValidator;
import com.cardbattle.engine.result.ActionResult;
import com.cardbattle.engine.result.PlayBlockReason;
import com.cardbattle.engine.state.GameStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.cardbattle.engine.TestCards.attack;
import static com.cardbattle.engine.TestCards.card;
import static com.cardbattle.engine.TestCards.effect;
import static com.cardbattle.engine.TestCards.pack;
import static com.cardbattle.engine.TestCards.tagged;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 2 필드 락(PRD 7.7)과 흐름 조작: 건너뛰기, 순서 반전, 행동 불가, 교차로, 시한폭탄, 무승부 카운트다운 */
class FieldAndFlowTest {

    private static CardDefinition misc(String id, Targeting targeting, EffectSpec... e) {
        return card(id, CardCategory.MISC, targeting, List.of(), e);
    }

    private static CardPack testPack() {
        return pack(
                attack("t.a10", 10),
                attack("t.a40", 40),
                tagged("t.fire", CardCategory.ATTACK, 10, Set.of("FIRE"), false),
                attack("t.water", 10, effect("FIELD_LOCK", "filter", Map.of("category", "ATTACK"), "turns", 2)),
                attack("t.swear", 15, effect("FIELD_LOCK",
                        "filter", Map.of("category", "ATTACK", "attackGte", 30), "turns", 2)),
                tagged("t.bat", CardCategory.ATTACK, 45, Set.of(), true),
                card("t.pass", CardCategory.SUPPORT, Targeting.NONE, List.of(),
                        effect("TRANSFER_ACCUMULATED", "to", "NEXT")),
                misc("t.jump", Targeting.NONE, effect("SKIP_NEXT")),
                misc("t.uturn", Targeting.NONE, effect("REVERSE_ORDER")),
                misc("t.cross", Targeting.CHOSEN_OTHER, effect("SET_NEXT_PLAYER", "target", "CHOSEN")),
                card("t.stop", CardCategory.BENEFIT, Targeting.NONE, List.of(),
                        effect("IMMUNE_THIS_TURN"), effect("LOCK_NEXT_PLAYER")),
                misc("t.bomb", Targeting.NONE, effect("TIME_BOMB", "p", 1.0, "damage", 50)),
                misc("t.dud", Targeting.NONE, effect("TIME_BOMB", "p", 0.0, "damage", 50,
                        "pByTag", Map.of("FIRE", 1.0))),
                misc("t.cafe", Targeting.NONE, effect("START_DRAW_COUNTDOWN", "base", 2, "perPlayer", 1)),
                misc("t.snow", Targeting.NONE, effect("DISPEL_STATUSES", "target", "ALL",
                        "filter", Map.of("statuses", List.of("TIME_BOMB")))));
    }

    private static TestGame game(String... players) {
        return TestGame.with(testPack(), players);
    }

    @Test
    @DisplayName("필드 락: 물총이 필드에 있으면 공격 카드를 낼 수 없고, 무조건 카드는 낼 수 있다")
    void fieldLockBlocksNextPlayer() {
        TestGame g = game("p1", "p2");
        g.play("p1", g.give("p1", "t.water"));
        ActionResult r = g.play("p2", g.give("p2", "t.a40"));
        assertEquals(PlayBlockReason.FIELD_LOCK, r.rejection().reason());
        assertTrue(g.play("p2", g.give("p2", "t.bat")).accepted());
    }

    @Test
    @DisplayName("필드 락: 공격력 필터 — 욕설은 30 이상만 막는다")
    void fieldLockFilter() {
        TestGame g = game("p1", "p2");
        g.play("p1", g.give("p1", "t.swear"));
        assertEquals(PlayBlockReason.FIELD_LOCK, g.play("p2", g.give("p2", "t.a40")).rejection().reason());
        assertTrue(g.play("p2", g.give("p2", "t.a10")).accepted());
    }

    @Test
    @DisplayName("필드 락: 다른 카드를 내서 필드가 바뀌면 풀린다")
    void fieldLockClearsWhenFieldReplaced() {
        TestGame g = game("p1", "p2", "p3");
        g.play("p1", g.give("p1", "t.water"));
        g.play("p2", g.give("p2", "t.pass"));
        assertTrue(g.state.getFieldLocks().isEmpty());
        assertTrue(g.play("p3", g.give("p3", "t.a40")).accepted());
    }

    @Test
    @DisplayName("필드 락: 버리기만 이어지면 최대 2턴 뒤 자동으로 풀린다 (SC1 무한 락 보완)")
    void fieldLockExpiresAfterTwoTurns() {
        TestGame g = game("p1", "p2");
        g.play("p1", g.give("p1", "t.water"));
        g.discard("p2", g.give("p2", "t.a10"));
        assertTrue(!g.state.getFieldLocks().isEmpty(), "1턴 지남: 아직 유지");
        g.discard("p1", g.give("p1", "t.a10"));
        assertTrue(g.state.getFieldLocks().isEmpty(), "2턴 지남: 해제");
        assertTrue(g.play("p2", g.give("p2", "t.a40")).accepted());
    }

    @Test
    @DisplayName("점프: 다음 사람을 건너뛰고, 누적은 그다음 사람이 받는다")
    void skipNext() {
        TestGame g = game("p1", "p2", "p3");
        g.play("p1", g.give("p1", "t.jump"));
        assertEquals("p3", g.current());
        assertEquals(1, g.events(EventType.TURN_SKIPPED).size());
    }

    @Test
    @DisplayName("유턴: 순서가 반대로 바뀌어 이전 사람 차례가 된다")
    void reverseOrder() {
        TestGame g = game("p1", "p2", "p3").turn("p2");
        g.play("p2", g.give("p2", "t.uturn"));
        assertEquals("p1", g.current());
        assertEquals(-1, g.state.getDirection());
    }

    @Test
    @DisplayName("교차로: 고른 사람이 다음 차례를 맡는다")
    void setNextPlayer() {
        TestGame g = game("p1", "p2", "p3", "p4");
        g.play("p1", g.give("p1", "t.cross"), "p3");
        assertEquals("p3", g.current());
    }

    @Test
    @DisplayName("시간아 멈춰라!: 나는 받지 않고, 다음 사람은 아무것도 못 한 채 누적을 받고 차례가 넘어간다")
    void lockNextPlayer() {
        TestGame g = game("p1", "p2", "p3").chain(30, 90);
        g.play("p1", g.give("p1", "t.stop"));
        assertEquals(200, g.p("p1").getHp());
        assertEquals(1, g.events(EventType.TURN_LOCKED).size());
        assertEquals("p3", g.current());
        // 이득 카드는 비공격이라 체인이 끝나므로 p2가 받을 누적은 없다. 체인이 이어지는 경우는 추가 제출(6단계)에서 확인
        assertEquals(200, g.p("p2").getHp());
    }

    @Test
    @DisplayName("랜덤시한폭탄: 설치한 턴부터 판정하고, 터지면 그 턴 행동한 사람이 50 피해")
    void timeBombExplodes() {
        TestGame g = game("p1", "p2");
        g.play("p1", g.give("p1", "t.bomb"));
        assertEquals(150, g.p("p1").getHp());
        assertNull(g.state.getTimeBomb());
        assertEquals(1, g.events(EventType.TIME_BOMB_EXPLODED).size());
    }

    @Test
    @DisplayName("랜덤시한폭탄: 화염 카드를 내면 그 턴 확률이 pByTag로 바뀐다")
    void timeBombTagChance() {
        TestGame g = game("p1", "p2");
        g.play("p1", g.give("p1", "t.dud"));
        assertNotNull(g.state.getTimeBomb());
        g.play("p2", g.give("p2", "t.a10"));
        assertNotNull(g.state.getTimeBomb(), "일반 카드: 확률 0");
        g.play("p1", g.give("p1", "t.fire"));
        assertNull(g.state.getTimeBomb(), "화염 카드: 확률 1");
        assertTrue(g.p("p1").getHp() < 200);
    }

    @Test
    @DisplayName("폭설: 설치된 시한폭탄을 없앤다")
    void dispelBomb() {
        TestGame g = game("p1", "p2");
        g.play("p1", g.give("p1", "t.dud"));
        g.play("p2", g.give("p2", "t.snow"));
        assertNull(g.state.getTimeBomb());
    }

    @Test
    @DisplayName("카페베네: (2 + 1×인원) 턴 뒤 무승부")
    void drawCountdown() {
        TestGame g = game("p1", "p2");
        g.play("p1", g.give("p1", "t.cafe"));
        assertEquals(3, g.state.getDrawCountdown(), "설치 턴 종료에서 4 → 3");
        g.discard("p2", g.give("p2", "t.a10"));
        g.discard("p1", g.give("p1", "t.a10"));
        assertEquals(GameStatus.IN_PROGRESS, g.state.getStatus());
        g.discard("p2", g.give("p2", "t.a10"));
        assertEquals(GameStatus.FINISHED, g.state.getStatus());
        assertTrue(g.state.getWinnerIds().isEmpty());
        assertEquals(Boolean.TRUE, g.events(EventType.GAME_ENDED).get(0).payload().get("draw"));
    }

    @Test
    @DisplayName("팩 검증: 필드 락은 최대 2턴")
    void validation() {
        PackValidator validator = new PackValidator(EffectRegistry.defaults());
        assertTrue(validator.validate(testPack()).isEmpty(), validator.validate(testPack()).toString());
        List<String> errors = validator.validate(pack(
                attack("t.forever", 10, effect("FIELD_LOCK", "filter", Map.of("category", "ATTACK"), "turns", 3))));
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.forever.effects[0].turns")), errors.toString());
    }
}
