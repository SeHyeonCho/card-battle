package com.cardbattle.engine;

import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.command.DiscardCommand;
import com.cardbattle.engine.command.PlayCommand;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.event.GameEvent;
import com.cardbattle.engine.result.ActionResult;
import com.cardbattle.engine.result.RejectCode;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.cardbattle.engine.TestCards.attack;
import static com.cardbattle.engine.TestCards.effect;
import static com.cardbattle.engine.TestCards.pack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 차례 전환 연출 (PRD FR-GAME-10): 전환 동안은 낼 수 없고, 제한 시간은 전환이 끝난 뒤부터 센다 */
class TurnTransitionTest {

    private static final int TRANSITION = GameSettings.TURN_TRANSITION_MS;
    private static final long START = TestGame.NOW.toEpochMilli();

    /** 테스트에서 시간을 앞으로 돌릴 수 있는 시계 */
    private static final class MovableClock extends Clock {
        private long millis = START;

        void advance(long ms) {
            millis += ms;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static CardPack testPack() {
        return pack(attack("t.a10", 10), attack("t.storm", 20, effect("EXTRA_PLAY", "count", 1)));
    }

    private final MovableClock clock = new MovableClock();
    private final GameEngine engine = new GameEngine(testPack(), EffectRegistry.defaults(), clock, TRANSITION);
    private final GameState state = engine.start("g1", GameSettings.defaults(),
            List.of(new PlayerSeed("a", "가"), new PlayerSeed("b", "나")), 3L).state();

    private ActionResult play(String playerId, String instanceId) {
        return engine.play(state, new PlayCommand(playerId, instanceId, null, null));
    }

    private String give(PlayerState player, String cardId) {
        String id = state.newInstanceId();
        player.getHand().add(new CardInstance(id, cardId));
        return id;
    }

    @Test
    @DisplayName("전환 동안은 내기·버리기가 거절되고 상태도 그대로다. 전환이 끝나면 낼 수 있다")
    void blocksActionsDuringTransition() {
        PlayerState current = state.currentPlayer();
        String card = give(current, "t.a10");
        long version = state.getVersion();

        ActionResult early = play(current.getPlayerId(), card);
        ActionResult earlyDiscard = engine.discard(state, new DiscardCommand(current.getPlayerId(), card, null));

        assertEquals(RejectCode.TURN_TRANSITION, early.rejection().code());
        assertEquals(RejectCode.TURN_TRANSITION, earlyDiscard.rejection().code());
        assertEquals(version, state.getVersion());
        assertTrue(current.getHand().stream().anyMatch(c -> c.instanceId().equals(card)));

        clock.advance(TRANSITION - 1);
        assertFalse(play(current.getPlayerId(), card).accepted(), "전환 시간이 1ms라도 남으면 거절");
        clock.advance(1);
        assertTrue(play(current.getPlayerId(), card).accepted());
    }

    @Test
    @DisplayName("남의 차례에는 전환 중이어도 NOT_YOUR_TURN 이 먼저다")
    void notYourTurnFirst() {
        PlayerState other = state.getPlayers().stream().filter(p -> p != state.currentPlayer()).findFirst().orElseThrow();
        String card = give(other, "t.a10");
        assertEquals(RejectCode.NOT_YOUR_TURN, play(other.getPlayerId(), card).rejection().code());
    }

    @Test
    @DisplayName("차례가 시작되면 전환이 끝나는 시각을 알리고, 마감은 전환 뒤부터 턴 시간만큼이다")
    void deadlineStartsAfterTransition() {
        assertEquals(START + TRANSITION, state.getTurnActiveFromEpochMs());
        assertEquals(START + TRANSITION + 25_000, state.getTurnDeadlineEpochMs());

        PlayerState first = state.currentPlayer();
        clock.advance(5_000);
        ActionResult result = play(first.getPlayerId(), give(first, "t.a10"));

        GameEvent started = result.events().stream().filter(e -> e.type() == EventType.TURN_STARTED)
                .findFirst().orElseThrow();
        Map<String, Object> p = started.payload();
        assertEquals(START + 5_000 + TRANSITION, p.get("activeFromEpochMs"));
        assertEquals(START + 5_000 + TRANSITION + 25_000, p.get("deadlineEpochMs"));
        assertEquals(TRANSITION, p.get("transitionMs"));

        PlayerState second = state.currentPlayer();
        String card = give(second, "t.a10");
        assertEquals(RejectCode.TURN_TRANSITION, play(second.getPlayerId(), card).rejection().code(),
                "다음 사람도 전환이 끝나야 낼 수 있다");
    }

    @Test
    @DisplayName("추가 제출은 같은 사람의 이어지는 차례라서 전환 없이 바로 낸다")
    void extraPlayHasNoTransition() {
        PlayerState current = state.currentPlayer();
        String storm = give(current, "t.storm");
        String next = give(current, "t.a10");
        clock.advance(TRANSITION);

        assertTrue(play(current.getPlayerId(), storm).accepted());
        assertTrue(state.getExtraPlay() != null);
        assertTrue(play(current.getPlayerId(), next).accepted());
    }

    @Test
    @DisplayName("스냅샷은 남은 전환 시간을 알려 준다 (새로고침해도 시계 차이 없이 기다린다)")
    void snapshotCarriesRemainingTransition() {
        clock.advance(400);
        assertEquals(TRANSITION - 400, engine.snapshot(state, "a").transitionRemainingMs());
        clock.advance(10_000);
        assertEquals(0, engine.snapshot(state, "a").transitionRemainingMs());
    }

    @Test
    @DisplayName("시간 초과 처리는 전환과 상관없이 된다")
    void timeoutIgnoresTransition() {
        assertTrue(engine.timeout(state).accepted());
    }
}
