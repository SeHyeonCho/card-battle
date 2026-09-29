package com.cardbattle.engine;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.Timing;
import com.cardbattle.engine.command.DiscardCommand;
import com.cardbattle.engine.command.PlayCommand;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.event.EventSink;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.event.GameEvent;
import com.cardbattle.engine.result.ActionResult;
import com.cardbattle.engine.result.Playability;
import com.cardbattle.engine.result.RejectCode;
import com.cardbattle.engine.result.Rejection;
import com.cardbattle.engine.rules.PlayabilityChecker;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.rules.TurnResolver;
import com.cardbattle.engine.state.FieldCard;
import com.cardbattle.engine.state.GameRng;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;
import com.cardbattle.engine.view.CardView;
import com.cardbattle.engine.view.GameSnapshot;
import com.cardbattle.engine.view.PlayerView;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static com.cardbattle.engine.event.EventSink.payload;

/**
 * 게임 규칙 엔진의 입구. Spring·Redis를 전혀 모르는 순수 Java 클래스라서
 * 서버 없이 단위 테스트로 규칙을 검증할 수 있다.
 *
 * <p>사용법: 상태(GameState)와 명령을 넘기면 상태를 바꾸고 이벤트 목록을 돌려준다.
 * 거절된 명령은 상태를 바꾸지 않는다.
 */
public final class GameEngine {

    private final CardPack pack;
    private final EffectRegistry effects;
    private final PlayabilityChecker playability;
    private final TurnResolver resolver;

    public GameEngine(CardPack pack) {
        this(pack, EffectRegistry.defaults(), Clock.systemUTC());
    }

    public GameEngine(CardPack pack, EffectRegistry effects, Clock clock) {
        this.pack = pack;
        this.effects = effects;
        this.playability = new PlayabilityChecker(pack);
        this.resolver = new TurnResolver(pack, effects, playability, clock);
    }

    public record StartResult(GameState state, List<GameEvent> events) {
    }

    // ------------------------------------------------------------------
    // 게임 시작 (PRD FR-GAME-01)
    // ------------------------------------------------------------------

    public StartResult start(String gameId, GameSettings settings, List<PlayerSeed> seeds, long seed) {
        if (seeds.size() < GameSettings.MIN_PLAYERS || seeds.size() > GameSettings.MAX_PLAYERS) {
            throw new IllegalArgumentException("players must be 2~6: " + seeds.size());
        }
        if (pack.totalWeight() <= 0) {
            throw new IllegalStateException("pack has no drawable cards: " + pack.code());
        }
        GameState state = new GameState();
        state.setGameId(gameId);
        state.setPackCode(pack.code());
        state.setPackVersion(pack.version());
        state.setSettings(settings);
        state.setRngState(seed);

        // 좌석 무작위 배정 (Fisher-Yates)
        List<PlayerSeed> order = new ArrayList<>(seeds);
        for (int i = order.size() - 1; i > 0; i--) {
            int j = GameRng.nextInt(state, i + 1);
            PlayerSeed tmp = order.get(i);
            order.set(i, order.get(j));
            order.set(j, tmp);
        }
        List<PlayerState> players = new ArrayList<>();
        for (int seat = 0; seat < order.size(); seat++) {
            PlayerSeed s = order.get(seat);
            players.add(new PlayerState(s.playerId(), s.nickname(), seat,
                    settings.startingHp(), settings.hpCap(), settings.handSize()));
        }
        state.setPlayers(players);
        state.setCurrentSeat(0);

        EventSink events = new EventSink(state);
        for (PlayerState p : state.getPlayers()) {
            resolver.drawUpTo(state, p);
        }
        events.toAll(EventType.GAME_STARTED, payload(
                "players", playerViews(state),
                "settings", settings,
                "packCode", pack.code(),
                "packVersion", pack.version()));
        for (PlayerState p : state.getPlayers()) {
            events.toPlayer(p.getPlayerId(), EventType.HAND_UPDATED, payload("hand", List.copyOf(p.getHand())));
        }
        resolver.startTurn(state, events);
        return new StartResult(state, events.commit());
    }

    // ------------------------------------------------------------------
    // 카드 제출 (PRD 7.3 → 7.4 → 7.6)
    // ------------------------------------------------------------------

    public ActionResult play(GameState state, PlayCommand cmd) {
        Rejection pre = precheck(state, cmd.playerId(), cmd.expectedVersion());
        if (pre != null) {
            return ActionResult.rejected(pre);
        }
        PlayerState actor = state.player(cmd.playerId());
        CardInstance inst = actor.findInHand(cmd.cardInstanceId());
        if (inst == null) {
            return ActionResult.rejected(Rejection.of(RejectCode.CARD_NOT_IN_HAND, "손패에 없는 카드입니다"));
        }
        CardDefinition card = requireCard(inst);
        if (card.targeting().requiresChoice() && cmd.targetId() == null) {
            return ActionResult.rejected(Rejection.of(RejectCode.INVALID_TARGET, "대상을 골라야 합니다"));
        }
        Playability check = playability.check(state, actor, card, cmd.targetId());
        if (!check.playable()) {
            return ActionResult.rejected(check.toRejection());
        }
        PlayerState chosen = card.targeting().requiresChoice() ? state.player(cmd.targetId()) : null;

        EventSink events = new EventSink(state);
        actor.getHand().remove(inst);
        actor.setConsecutiveTimeouts(0);
        int attack = card.attackCard() ? rollAttack(state, card) : 0;
        TurnContext ctx = new TurnContext(state, pack, actor, card, attack, chosen, events);

        events.toAll(EventType.CARD_PLAYED, payload(
                "playerId", actor.getPlayerId(),
                "cardId", card.id(),
                "instanceId", inst.instanceId(),
                "attack", card.attackCard() ? attack : null,
                "targetId", chosen == null ? null : chosen.getPlayerId()));

        // 효과와 조건은 "직전 필드"를 본다. 필드 교체는 판정 뒤에 한다 (7.7)
        effects.run(ctx, card.effectsAt(Timing.ON_PLAY));
        resolver.judge(ctx);
        state.setField(List.of(new FieldCard(inst.instanceId(), card.id(), actor.getPlayerId(), attack,
                state.getTurnNumber())));
        events.toAll(EventType.FIELD_CHANGED, payload("field", List.copyOf(state.getField())));

        resolver.settle(ctx);
        return ActionResult.accepted(events.commit());
    }

    // ------------------------------------------------------------------
    // 카드 버리기 — 필드는 바뀌지 않는다 (7.7)
    // ------------------------------------------------------------------

    public ActionResult discard(GameState state, DiscardCommand cmd) {
        Rejection pre = precheck(state, cmd.playerId(), cmd.expectedVersion());
        if (pre != null) {
            return ActionResult.rejected(pre);
        }
        PlayerState actor = state.player(cmd.playerId());
        CardInstance inst = actor.findInHand(cmd.cardInstanceId());
        if (inst == null) {
            return ActionResult.rejected(Rejection.of(RejectCode.CARD_NOT_IN_HAND, "손패에 없는 카드입니다"));
        }
        EventSink events = new EventSink(state);
        actor.getHand().remove(inst);
        actor.setConsecutiveTimeouts(0);
        events.toAll(EventType.CARD_DISCARDED, payload(
                "playerId", actor.getPlayerId(), "cardId", inst.cardId(), "instanceId", inst.instanceId()));
        TurnContext ctx = new TurnContext(state, pack, actor, null, 0, null, events);
        resolver.judge(ctx);
        resolver.settle(ctx);
        return ActionResult.accepted(events.commit());
    }

    // ------------------------------------------------------------------
    // 턴 시간 초과 — 무작위 카드 1장을 버린다 (FR-GAME-06)
    // ------------------------------------------------------------------

    public ActionResult timeout(GameState state) {
        if (!state.inProgress()) {
            return ActionResult.rejected(Rejection.of(RejectCode.GAME_FINISHED, "이미 끝난 게임입니다"));
        }
        PlayerState actor = state.currentPlayer();
        EventSink events = new EventSink(state);
        String cardId = null;
        String instanceId = null;
        if (!actor.getHand().isEmpty()) {
            CardInstance inst = actor.getHand().remove(GameRng.nextInt(state, actor.getHand().size()));
            cardId = inst.cardId();
            instanceId = inst.instanceId();
        }
        actor.setConsecutiveTimeouts(actor.getConsecutiveTimeouts() + 1);
        events.toAll(EventType.TURN_TIMED_OUT, payload(
                "playerId", actor.getPlayerId(), "cardId", cardId, "instanceId", instanceId,
                "consecutiveTimeouts", actor.getConsecutiveTimeouts()));
        TurnContext ctx = new TurnContext(state, pack, actor, null, 0, null, events);
        resolver.judge(ctx);
        resolver.settle(ctx);
        return ActionResult.accepted(events.commit());
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    /** 특정 플레이어 시점의 스냅샷 (재접속용). 다른 사람의 손패는 넣지 않는다 */
    public GameSnapshot snapshot(GameState state, String viewerId) {
        PlayerState viewer = state.player(viewerId);
        List<CardInstance> myHand = viewer == null ? List.of() : List.copyOf(viewer.getHand());
        boolean myTurn = viewer != null && state.inProgress() && viewer == state.currentPlayer();
        return new GameSnapshot(
                state.getGameId(),
                state.getVersion(),
                state.getNextSeq() - 1,
                state.getSettings(),
                state.getStatus(),
                state.getTurnNumber(),
                state.currentPlayer().getPlayerId(),
                state.getDirection(),
                state.getCurrentAttack(),
                state.getAccumulatedDamage(),
                List.copyOf(state.getField()),
                playerViews(state),
                viewerId,
                myHand,
                myTurn ? resolver.playabilityOf(state, viewer) : List.of(),
                state.getTurnDeadlineEpochMs(),
                List.copyOf(state.getWinnerIds()),
                pack.code(),
                pack.version(),
                pack.cards().stream().map(CardView::of).toList());
    }

    private static List<PlayerView> playerViews(GameState state) {
        return state.getPlayers().stream()
                .map(p -> new PlayerView(p.getPlayerId(), p.getNickname(), p.getSeat(), p.getHp(), p.getHpCap(),
                        p.getHand().size(), p.isEliminated()))
                .toList();
    }

    // ------------------------------------------------------------------
    // 내부 도우미
    // ------------------------------------------------------------------

    /** PRD 7.3의 1단계와 공통 검사 */
    private Rejection precheck(GameState state, String playerId, Long expectedVersion) {
        if (!state.inProgress()) {
            return Rejection.of(RejectCode.GAME_FINISHED, "이미 끝난 게임입니다");
        }
        if (expectedVersion != null && expectedVersion != state.getVersion()) {
            return Rejection.of(RejectCode.STALE_VERSION, "화면이 최신 상태가 아닙니다. 다시 불러옵니다");
        }
        PlayerState player = state.player(playerId);
        if (player == null) {
            return Rejection.of(RejectCode.PLAYER_NOT_FOUND, "이 게임의 참가자가 아닙니다");
        }
        if (player != state.currentPlayer()) {
            return Rejection.of(RejectCode.NOT_YOUR_TURN, "내 차례가 아닙니다");
        }
        return null;
    }

    private CardDefinition requireCard(CardInstance inst) {
        CardDefinition card = pack.card(inst.cardId());
        if (card == null) {
            throw new IllegalStateException("card not in pack " + pack.code() + ": " + inst.cardId());
        }
        return card;
    }

    private static int rollAttack(GameState state, CardDefinition card) {
        if (card.randomAttack()) {
            return GameRng.between(state, card.attackMin(), card.attackMax());
        }
        return card.attack();
    }
}
