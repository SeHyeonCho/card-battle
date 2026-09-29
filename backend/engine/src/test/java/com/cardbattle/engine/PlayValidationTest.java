package com.cardbattle.engine;

import com.cardbattle.engine.command.PlayCommand;
import com.cardbattle.engine.result.ActionResult;
import com.cardbattle.engine.result.PlayBlockReason;
import com.cardbattle.engine.result.RejectCode;
import com.cardbattle.engine.state.GameStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PRD 7.3 제출 가능 판정과 9.5 거절 코드. */
class PlayValidationTest {

    @Test
    @DisplayName("내 차례가 아니면 NOT_YOUR_TURN")
    void notYourTurn() {
        TestGame g = TestGame.of("p1", "p2");
        ActionResult r = g.play("p2", g.give("p2", "t.a10"));

        assertFalse(r.accepted());
        assertEquals(RejectCode.NOT_YOUR_TURN, r.rejection().code());
    }

    @Test
    @DisplayName("손패에 없는 카드면 CARD_NOT_IN_HAND")
    void cardNotInHand() {
        TestGame g = TestGame.of("p1", "p2");
        ActionResult r = g.play("p1", "no-such-card");

        assertEquals(RejectCode.CARD_NOT_IN_HAND, r.rejection().code());
    }

    @Test
    @DisplayName("제출 조건을 만족하지 않으면 CARD_NOT_PLAYABLE / CONDITION_UNMET")
    void conditionUnmet() {
        TestGame g = TestGame.of("p1", "p2"); // 체력 200 > 100
        ActionResult r = g.play("p1", g.give("p1", "t.lowhp"));

        assertEquals(RejectCode.CARD_NOT_PLAYABLE, r.rejection().code());
        assertEquals(PlayBlockReason.CONDITION_UNMET, r.rejection().reason());
    }

    @Test
    @DisplayName("제출 조건을 만족하면 낼 수 있다")
    void conditionMet() {
        TestGame g = TestGame.of("p1", "p2").hp("p1", 100);
        assertTrue(g.play("p1", g.give("p1", "t.lowhp")).accepted());
    }

    @Test
    @DisplayName("대상이 필요한 카드에 대상을 안 고르면 INVALID_TARGET")
    void missingTarget() {
        TestGame g = TestGame.of("p1", "p2");
        assertEquals(RejectCode.INVALID_TARGET, g.play("p1", g.give("p1", "t.snipe")).rejection().code());
    }

    @Test
    @DisplayName("CHOSEN_OTHER 카드로 자기 자신을 고르면 INVALID_TARGET")
    void cannotTargetSelf() {
        TestGame g = TestGame.of("p1", "p2");
        assertEquals(RejectCode.INVALID_TARGET, g.play("p1", g.give("p1", "t.snipe"), "p1").rejection().code());
    }

    @Test
    @DisplayName("탈락한 사람은 대상으로 고를 수 없다")
    void cannotTargetEliminated() {
        TestGame g = TestGame.of("p1", "p2", "p3").eliminated("p3");
        assertEquals(RejectCode.INVALID_TARGET, g.play("p1", g.give("p1", "t.snipe"), "p3").rejection().code());
    }

    @Test
    @DisplayName("보고 있던 버전이 다르면 STALE_VERSION")
    void staleVersion() {
        TestGame g = TestGame.of("p1", "p2");
        String card = g.give("p1", "t.a10");
        ActionResult r = g.engine.play(g.state, new PlayCommand("p1", card, null, 999L));

        assertEquals(RejectCode.STALE_VERSION, r.rejection().code());
    }

    @Test
    @DisplayName("끝난 게임에는 행동할 수 없다")
    void finishedGameRejects() {
        TestGame g = TestGame.of("p1", "p2");
        g.state.setStatus(GameStatus.FINISHED);

        assertEquals(RejectCode.GAME_FINISHED, g.play("p1", g.give("p1", "t.a10")).rejection().code());
        assertEquals(RejectCode.GAME_FINISHED, g.timeout().rejection().code());
    }

    @Test
    @DisplayName("거절된 행동은 상태를 바꾸지 않는다")
    void rejectionDoesNotChangeState() {
        TestGame g = TestGame.of("p1", "p2").chain(30, 60);
        String card = g.give("p1", "t.snipe");
        long versionBefore = g.state.getVersion();
        long seqBefore = g.state.getNextSeq();

        ActionResult r = g.play("p1", card, "p1");

        assertFalse(r.accepted());
        assertTrue(r.events().isEmpty());
        assertEquals(versionBefore, g.state.getVersion());
        assertEquals(seqBefore, g.state.getNextSeq());
        assertEquals(1, g.p("p1").getHand().size());
        assertEquals(60, g.state.getAccumulatedDamage());
    }
}
