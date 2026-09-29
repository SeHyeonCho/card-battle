package com.cardbattle.engine;

import com.cardbattle.engine.card.CardCategory;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.Targeting;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.event.GameEvent;
import com.cardbattle.engine.result.ActionResult;
import com.cardbattle.engine.result.RejectCode;
import com.cardbattle.engine.state.ExtraPlayState;
import com.cardbattle.engine.state.GameStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.cardbattle.engine.TestCards.attack;
import static com.cardbattle.engine.TestCards.card;
import static com.cardbattle.engine.TestCards.effect;
import static com.cardbattle.engine.TestCards.pack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 연속 시간 초과 → 자리 비움, 자리 비움인 플레이어 강퇴 (PRD 4.3, FR-GAME-07) */
class AwayAndKickTest {

    private static CardPack testPack() {
        return pack(
                attack("t.a10", 10),
                card("t.lego", CardCategory.CURSE, Targeting.CHOSEN_OTHER, List.of(),
                        effect("APPLY_CURSE", "target", "CHOSEN", "curse", Map.of("id", "curse.lego",
                                "onTurnEnd", List.of(Map.of("type", "DAMAGE", "target", "CURSED", "amount", 10))))));
    }

    private static TestGame game(String... players) {
        return TestGame.with(testPack(), players);
    }

    /** playerId 차례가 올 때까지 다른 사람은 카드를 버리고, playerId는 시간 초과한다 (times번) */
    private static void timeOut(TestGame g, String playerId, int times) {
        for (int i = 0; i < times; i++) {
            while (!g.current().equals(playerId)) {
                assertTrue(g.discard(g.current(), g.give(g.current(), "t.a10")).accepted());
            }
            assertTrue(g.timeout().accepted());
        }
    }

    private static List<Map<String, Object>> awayChanges(TestGame g) {
        return g.events(EventType.PLAYER_AWAY_CHANGED).stream().map(GameEvent::payload).toList();
    }

    @Test
    @DisplayName("3번 연속 시간 초과하면 자리 비움이 되고, 모두에게 알린다")
    void threeTimeoutsMarkAway() {
        TestGame g = game("p1", "p2").turn("p1");
        timeOut(g, "p1", 2);
        assertFalse(g.p("p1").away());
        assertTrue(awayChanges(g).isEmpty());

        timeOut(g, "p1", 1);

        assertTrue(g.p("p1").away());
        assertEquals(List.of(Map.of("playerId", "p1", "away", true)), awayChanges(g));
        assertTrue(g.engine.snapshot(g.state, "p2").players().get(0).away());

        timeOut(g, "p1", 1);
        assertEquals(1, awayChanges(g).size(), "이미 자리 비움이면 다시 알리지 않는다");
    }

    @Test
    @DisplayName("자리 비움인 사람이 직접 행동하면 자리 비움이 풀린다")
    void actingClearsAway() {
        TestGame g = game("p1", "p2").turn("p1");
        timeOut(g, "p1", 3);
        g.discard("p2", g.give("p2", "t.a10"));

        g.play("p1", g.give("p1", "t.a10"));

        assertFalse(g.p("p1").away());
        assertEquals(Map.of("playerId", "p1", "away", false), awayChanges(g).get(1));
    }

    @Test
    @DisplayName("자리 비움이 아닌 사람, 없는 사람, 탈락한 사람은 강퇴할 수 없다")
    void kickRequiresAwayPlayer() {
        TestGame g = game("p1", "p2", "p3").turn("p1").eliminated("p3");
        g.p("p2").setConsecutiveTimeouts(2);

        assertEquals(RejectCode.PLAYER_NOT_AWAY, g.engine.kick(g.state, "p2").rejection().code());
        assertEquals(RejectCode.PLAYER_NOT_FOUND, g.engine.kick(g.state, "nobody").rejection().code());
        g.p("p3").setConsecutiveTimeouts(5);
        assertEquals(RejectCode.PLAYER_NOT_FOUND, g.engine.kick(g.state, "p3").rejection().code());
        assertTrue(g.p("p2").alive());
    }

    @Test
    @DisplayName("차례가 아닌 사람을 강퇴하면 탈락만 하고 지금 차례는 그대로다")
    void kickWaitingPlayer() {
        TestGame g = game("p1", "p2", "p3").turn("p1").chain(20, 50);
        g.give("p3", "t.a10");
        g.p("p3").setConsecutiveTimeouts(3);
        long version = g.state.getVersion();
        int turn = g.state.getTurnNumber();

        ActionResult result = g.engine.kick(g.state, "p3");

        assertTrue(result.accepted());
        assertFalse(g.p("p3").alive());
        assertTrue(g.p("p3").getHand().isEmpty());
        assertEquals(200, g.p("p3").getHp(), "체력은 건드리지 않는다");
        assertEquals("p1", g.current());
        assertEquals(turn, g.state.getTurnNumber());
        assertEquals(20, g.state.getCurrentAttack());
        assertEquals(50, g.state.getAccumulatedDamage());
        assertEquals(version + 1, g.state.getVersion());
        GameEvent out = result.events().stream().filter(e -> e.type() == EventType.PLAYER_ELIMINATED).findFirst()
                .orElseThrow();
        assertEquals("KICKED", out.payload().get("reason"));
        assertTrue(result.events().stream().anyMatch(e -> e.type() == EventType.PLAYABILITY_UPDATED
                && "p1".equals(e.recipientId())));
    }

    @Test
    @DisplayName("차례인 사람을 강퇴하면 누적은 사라지고 추가 제출도 취소되며 다음 사람 차례가 된다")
    void kickCurrentPlayer() {
        TestGame g = game("p1", "p2", "p3").turn("p2").chain(20, 50);
        g.p("p2").setConsecutiveTimeouts(3);
        g.state.setExtraPlay(new ExtraPlayState());
        int turn = g.state.getTurnNumber();

        ActionResult result = g.engine.kick(g.state, "p2");

        assertTrue(result.accepted());
        assertEquals("p3", g.current());
        assertEquals(turn + 1, g.state.getTurnNumber());
        assertEquals(0, g.state.getCurrentAttack());
        assertEquals(0, g.state.getAccumulatedDamage());
        assertNull(g.state.getExtraPlay());
        assertEquals(200, g.p("p1").getHp());
        assertEquals(200, g.p("p3").getHp());
        List<EventType> types = result.events().stream().map(GameEvent::type).toList();
        assertTrue(types.indexOf(EventType.TURN_ENDED) < types.indexOf(EventType.TURN_STARTED));
    }

    @Test
    @DisplayName("강퇴된 사람이 건 저주는 풀린다")
    void kickRemovesCasterCurses() {
        TestGame g = game("p1", "p2", "p3").turn("p1");
        assertTrue(g.play("p1", g.give("p1", "t.lego"), "p2").accepted());
        g.p("p1").setConsecutiveTimeouts(3);

        g.engine.kick(g.state, "p1");

        assertNull(g.p("p2").getCurse());
    }

    @Test
    @DisplayName("강퇴로 한 명만 남으면 그 사람이 이기고 게임이 끝난다")
    void kickEndsGame() {
        TestGame g = game("p1", "p2").turn("p1");
        g.p("p2").setConsecutiveTimeouts(3);

        ActionResult result = g.engine.kick(g.state, "p2");

        assertTrue(result.accepted());
        assertEquals(GameStatus.FINISHED, g.state.getStatus());
        assertEquals(List.of("p1"), g.state.getWinnerIds());
        assertEquals(RejectCode.GAME_FINISHED, g.engine.kick(g.state, "p2").rejection().code());
    }
}
