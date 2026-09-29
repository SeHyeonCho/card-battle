package com.cardbattle.engine;

import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.event.GameEvent;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;
import com.cardbattle.engine.view.GameSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 게임 시작, 시간 초과, 버전·이벤트 번호, 스냅샷. */
class GameFlowTest {

    private static final List<PlayerSeed> FOUR = List.of(
            new PlayerSeed("a", "민수"), new PlayerSeed("b", "지훈"),
            new PlayerSeed("c", "서연"), new PlayerSeed("d", "하늘"));

    private GameEngine engine() {
        return new GameEngine(TestCards.standardPack(), EffectRegistry.defaults(), TestGame.CLOCK);
    }

    @Test
    @DisplayName("시작하면 모두 손패 5장을 받고 첫 턴이 시작된다")
    void startDealsHandsAndStartsFirstTurn() {
        GameEngine.StartResult start = engine().start("g1", GameSettings.defaults(), FOUR, 7L);
        GameState s = start.state();

        assertEquals(4, s.getPlayers().size());
        for (PlayerState p : s.getPlayers()) {
            assertEquals(5, p.getHand().size());
            assertEquals(200, p.getHp());
        }
        assertEquals(1, s.getTurnNumber());
        assertEquals(1, s.getVersion());

        List<GameEvent> events = start.events();
        assertEquals(EventType.GAME_STARTED, events.get(0).type());
        long privateHands = events.stream().filter(e -> e.type() == EventType.HAND_UPDATED).count();
        assertEquals(4, privateHands);
        GameEvent playability = events.stream()
                .filter(e -> e.type() == EventType.PLAYABILITY_UPDATED).findFirst().orElseThrow();
        assertEquals(s.currentPlayer().getPlayerId(), playability.recipientId());
    }

    @Test
    @DisplayName("같은 시드면 좌석과 손패가 똑같이 재현된다")
    void sameSeedSameGame() {
        GameState s1 = engine().start("g1", GameSettings.defaults(), FOUR, 1234L).state();
        GameState s2 = engine().start("g2", GameSettings.defaults(), FOUR, 1234L).state();

        for (int i = 0; i < 4; i++) {
            assertEquals(s1.getPlayers().get(i).getPlayerId(), s2.getPlayers().get(i).getPlayerId());
            assertEquals(s1.getPlayers().get(i).getHand(), s2.getPlayers().get(i).getHand());
        }
    }

    @Test
    @DisplayName("인원은 2~6명만 허용한다")
    void rejectsInvalidPlayerCount() {
        assertThrows(IllegalArgumentException.class,
                () -> engine().start("g1", GameSettings.defaults(), List.of(new PlayerSeed("a", "a")), 1L));
    }

    @Test
    @DisplayName("턴 마감 시각은 시계 + 제한 시간")
    void deadlineUsesClock() {
        GameState s = engine().start("g1", GameSettings.defaults(), FOUR, 1L).state();
        assertEquals(TestGame.NOW.toEpochMilli() + 25_000, s.getTurnDeadlineEpochMs());
    }

    @Test
    @DisplayName("시간 초과면 무작위 카드를 버리고, 누적이 있으면 받는다")
    void timeoutDiscardsRandomCard() {
        TestGame g = TestGame.of("p1", "p2").chain(30, 60);
        g.give("p1", "t.a5");
        g.give("p1", "t.a10");

        assertTrue(g.timeout().accepted());

        assertEquals(140, g.p("p1").getHp());
        assertEquals(1, g.p("p1").getConsecutiveTimeouts());
        assertEquals(5, g.p("p1").getHand().size());
        assertEquals("p2", g.current());
        assertNotNull(g.events(EventType.TURN_TIMED_OUT).get(0).payload().get("cardId"));
    }

    @Test
    @DisplayName("직접 행동하면 연속 시간 초과 횟수가 초기화된다")
    void manualActionResetsTimeouts() {
        TestGame g = TestGame.of("p1", "p2");
        g.p("p1").setConsecutiveTimeouts(2);
        g.play("p1", g.give("p1", "t.a10"));

        assertEquals(0, g.p("p1").getConsecutiveTimeouts());
    }

    @Test
    @DisplayName("행동마다 버전은 1씩, 이벤트 번호는 빠짐없이 오른다")
    void versionAndSeqIncrease() {
        TestGame g = TestGame.of("p1", "p2");
        long v = g.state.getVersion();
        long seq = g.state.getNextSeq();

        List<GameEvent> first = g.play("p1", g.give("p1", "t.a10")).events();
        List<GameEvent> second = g.play("p2", g.give("p2", "t.a20")).events();

        assertEquals(v + 2, g.state.getVersion());
        long expected = seq;
        for (GameEvent e : first) {
            assertEquals(expected++, e.seq());
            assertEquals(v + 1, e.version());
        }
        for (GameEvent e : second) {
            assertEquals(expected++, e.seq());
            assertEquals(v + 2, e.version());
        }
    }

    @Test
    @DisplayName("스냅샷에는 내 손패만 들어가고, 다른 사람은 장수만 보인다")
    void snapshotHidesOtherHands() {
        GameEngine engine = engine();
        GameState s = engine.start("g1", GameSettings.defaults(), FOUR, 99L).state();
        String viewer = s.getPlayers().get(2).getPlayerId();

        GameSnapshot snap = engine.snapshot(s, viewer);

        assertEquals(s.player(viewer).getHand(), snap.myHand());
        assertEquals(4, snap.players().size());
        assertEquals(5, snap.players().get(0).handCount());
        assertTrue(snap.playability().isEmpty()); // 내 차례가 아니면 비어 있다
        assertEquals(TestCards.standardPack().cards().size(), snap.cards().size());
    }

    @Test
    @DisplayName("스냅샷의 최근 이벤트에는 공개 이벤트만, 오래된 것부터 담긴다 (새로고침 후 게임 로그 복원)")
    void snapshotCarriesRecentPublicEvents() {
        GameEngine engine = engine();
        GameEngine.StartResult start = engine.start("g1", GameSettings.defaults(), FOUR, 99L);
        GameState s = start.state();
        List<GameEvent> history = new ArrayList<>(start.events());
        history.addAll(engine.timeout(s).events());
        String viewer = s.getPlayers().get(2).getPlayerId();

        GameSnapshot snap = engine.snapshot(s, viewer, history);

        assertFalse(snap.recentEvents().isEmpty());
        assertTrue(snap.recentEvents().stream().allMatch(GameEvent::publicEvent), "남의 손패가 담긴 개인 이벤트는 빠진다");
        assertEquals(history.stream().filter(GameEvent::publicEvent).toList(), snap.recentEvents());
        assertEquals(EventType.GAME_STARTED, snap.recentEvents().get(0).type());
        assertEquals(snap.lastSeq(), history.get(history.size() - 1).seq());
        assertTrue(engine.snapshot(s, viewer).recentEvents().isEmpty());
    }

    @Test
    @DisplayName("스냅샷의 최근 이벤트는 개수 제한만큼 가장 최근 것만 담는다")
    void snapshotRecentEventsAreCapped() {
        GameEngine engine = engine();
        GameEngine.StartResult start = engine.start("g1", GameSettings.defaults(), FOUR, 5L);
        GameState s = start.state();
        List<GameEvent> history = new ArrayList<>(start.events());
        while (history.stream().filter(GameEvent::publicEvent).count() <= GameEngine.RECENT_EVENT_LIMIT
                && s.inProgress()) {
            history.addAll(engine.timeout(s).events());
        }
        List<GameEvent> publicEvents = history.stream().filter(GameEvent::publicEvent).toList();

        List<GameEvent> recent = engine.snapshot(s, "a", history).recentEvents();

        assertEquals(GameEngine.RECENT_EVENT_LIMIT, recent.size());
        assertEquals(publicEvents.get(publicEvents.size() - 1), recent.get(recent.size() - 1));
    }
}
