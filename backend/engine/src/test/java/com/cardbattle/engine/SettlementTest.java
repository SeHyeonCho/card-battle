package com.cardbattle.engine;

import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.event.GameEvent;
import com.cardbattle.engine.state.GameStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PRD 7.6 정산, 7.8 탈락, 7.10 게임 종료. */
class SettlementTest {

    @Test
    @DisplayName("누적을 받고 체력이 0 이하가 되면 탈락하고, 새 체인은 다음 사람에게 간다")
    void lethalReceiveEliminates() {
        TestGame g = TestGame.of("p1", "p2", "p3").hp("p1", 50).chain(30, 60);
        g.play("p1", g.give("p1", "t.a10"));

        assertTrue(g.p("p1").isEliminated());
        assertTrue(g.p("p1").getHand().isEmpty());
        assertEquals(GameStatus.IN_PROGRESS, g.state.getStatus());
        assertEquals("p2", g.current());
        assertEquals(10, g.state.getAccumulatedDamage());
        assertEquals(1, g.events(EventType.PLAYER_ELIMINATED).size());
    }

    @Test
    @DisplayName("탈락한 플레이어는 턴 순서에서 건너뛴다")
    void turnOrderSkipsEliminated() {
        TestGame g = TestGame.of("p1", "p2", "p3").eliminated("p2");
        g.play("p1", g.give("p1", "t.a10"));

        assertEquals("p3", g.current());
    }

    @Test
    @DisplayName("한 명만 남으면 그 사람이 승리한다")
    void lastPlayerStandingWins() {
        TestGame g = TestGame.of("p1", "p2").turn("p2").hp("p2", 30).chain(30, 60);
        g.discard("p2", g.give("p2", "t.a5"));

        assertEquals(GameStatus.FINISHED, g.state.getStatus());
        assertEquals(List.of("p1"), g.state.getWinnerIds());
        GameEvent ended = g.events(EventType.GAME_ENDED).get(0);
        assertEquals(false, ended.payload().get("draw"));
        List<?> ranking = (List<?>) ended.payload().get("ranking");
        assertEquals(1, ((Map<?, ?>) ranking.get(0)).get("rank"));
        assertEquals("p1", ((Map<?, ?>) ranking.get(0)).get("playerId"));
        assertEquals(2, ((Map<?, ?>) ranking.get(1)).get("rank"));
    }

    @Test
    @DisplayName("같은 정산에서 남은 전원이 탈락하면 무승부, 공동 1위")
    void simultaneousEliminationIsDraw() {
        TestGame g = TestGame.of("p1", "p2").hp("p1", 10).chain(30, 60);
        g.play("p1", g.give("p1", "t.bomb")); // p2에게 999, 그 뒤 p1이 누적 60을 받음

        assertEquals(GameStatus.FINISHED, g.state.getStatus());
        assertTrue(g.state.getWinnerIds().isEmpty());
        GameEvent ended = g.events(EventType.GAME_ENDED).get(0);
        assertEquals(true, ended.payload().get("draw"));
        List<?> ranking = (List<?>) ended.payload().get("ranking");
        assertEquals(1, ((Map<?, ?>) ranking.get(0)).get("rank"));
        assertEquals(1, ((Map<?, ?>) ranking.get(1)).get("rank"));
    }

    @Test
    @DisplayName("정산 도중 0 이하로 내려갔다가 회복하면 살아남는다")
    void recoveringBeforeEliminationCheckSurvives() {
        TestGame g = TestGame.of("p1", "p2").hp("p1", 30);
        g.play("p1", g.give("p1", "t.phoenix")); // 즉시 -50, 턴 종료 시 +50

        assertFalse(g.p("p1").isEliminated());
        assertEquals(30, g.p("p1").getHp());
    }

    @Test
    @DisplayName("회복은 체력 상한을 넘지 않는다")
    void healIsCappedAtHpCap() {
        TestGame g = TestGame.of("p1", "p2").hp("p1", 490);
        g.play("p1", g.give("p1", "t.heal20"));

        assertEquals(500, g.p("p1").getHp());
    }

    @Test
    @DisplayName("다른 사람을 공격하는 이득 카드는 지정한 대상만 맞는다")
    void pokeDamagesOnlyTarget() {
        TestGame g = TestGame.of("p1", "p2", "p3");
        g.play("p1", g.give("p1", "t.poke"), "p3");

        assertEquals(200, g.p("p2").getHp());
        assertEquals(185, g.p("p3").getHp());
    }

    @Test
    @DisplayName("행동이 끝나면 손패가 한도까지 다시 채워진다")
    void handIsRefilledAfterAction() {
        TestGame g = TestGame.of("p1", "p2");
        g.play("p1", g.give("p1", "t.a10"));

        assertEquals(5, g.p("p1").getHand().size());
        assertEquals(1, g.events(EventType.HAND_UPDATED).size());
        assertEquals("p1", g.events(EventType.HAND_UPDATED).get(0).recipientId());
    }
}
