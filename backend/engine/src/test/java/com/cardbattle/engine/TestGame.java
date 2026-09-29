package com.cardbattle.engine;

import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.command.DiscardCommand;
import com.cardbattle.engine.command.PlayCommand;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.event.GameEvent;
import com.cardbattle.engine.result.ActionResult;
import com.cardbattle.engine.state.FieldCard;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 원하는 상황(체력, 현재 공격력, 누적 데미지, 손패)을 직접 만들어 규칙을 검증하는 도우미.
 * 무작위 시작(GameEngine.start)을 거치지 않으므로 테스트가 결정적이다.
 */
final class TestGame {

    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    final CardPack pack;
    final GameEngine engine;
    final GameState state;
    private final List<GameEvent> events = new ArrayList<>();

    private TestGame(CardPack pack, String... playerIds) {
        this.pack = pack;
        this.engine = new GameEngine(pack, EffectRegistry.defaults(), CLOCK);
        this.state = new GameState();
        state.setGameId("g1");
        state.setPackCode(pack.code());
        state.setPackVersion(pack.version());
        state.setSettings(GameSettings.defaults());
        state.setRngState(42);
        state.setTurnNumber(1);
        state.setVersion(1);
        List<PlayerState> players = new ArrayList<>();
        for (int seat = 0; seat < playerIds.length; seat++) {
            players.add(new PlayerState(playerIds[seat], playerIds[seat], seat, 200, 500, 5));
        }
        state.setPlayers(players);
    }

    static TestGame of(String... playerIds) {
        return new TestGame(TestCards.standardPack(), playerIds);
    }

    static TestGame with(CardPack pack, String... playerIds) {
        return new TestGame(pack, playerIds);
    }

    PlayerState p(String playerId) {
        return state.player(playerId);
    }

    TestGame hp(String playerId, int hp) {
        p(playerId).setHp(hp);
        return this;
    }

    TestGame chain(int currentAttack, int accumulatedDamage) {
        state.setCurrentAttack(currentAttack);
        state.setAccumulatedDamage(accumulatedDamage);
        return this;
    }

    TestGame turn(String playerId) {
        state.setCurrentSeat(p(playerId).getSeat());
        return this;
    }

    TestGame eliminated(String playerId) {
        p(playerId).setEliminated(true);
        p(playerId).setHp(0);
        return this;
    }

    TestGame field(String cardId) {
        state.setField(List.of(new FieldCard("f1", cardId, "someone", 0, 0)));
        return this;
    }

    /** 손패에 카드를 넣고 instanceId를 돌려준다 */
    String give(String playerId, String cardId) {
        String instanceId = state.newInstanceId();
        p(playerId).getHand().add(new CardInstance(instanceId, cardId));
        return instanceId;
    }

    ActionResult play(String playerId, String instanceId) {
        return play(playerId, instanceId, null);
    }

    ActionResult play(String playerId, String instanceId, String targetId) {
        return record(engine.play(state, new PlayCommand(playerId, instanceId, targetId, null)));
    }

    ActionResult discard(String playerId, String instanceId) {
        return record(engine.discard(state, new DiscardCommand(playerId, instanceId, null)));
    }

    ActionResult timeout() {
        return record(engine.timeout(state));
    }

    private ActionResult record(ActionResult result) {
        events.addAll(result.events());
        return result;
    }

    List<GameEvent> events(EventType type) {
        return events.stream().filter(e -> e.type() == type).toList();
    }

    String current() {
        return state.currentPlayer().getPlayerId();
    }
}
