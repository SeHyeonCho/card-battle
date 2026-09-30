package com.cardbattle.engine;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.card.Timing;
import com.cardbattle.engine.command.DiscardCommand;
import com.cardbattle.engine.command.PlayCommand;
import com.cardbattle.engine.effect.ConditionalAttackEffect;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.event.EventSink;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.event.GameEvent;
import com.cardbattle.engine.pack.PackParser;
import com.cardbattle.engine.result.ActionResult;
import com.cardbattle.engine.result.Playability;
import com.cardbattle.engine.result.RejectCode;
import com.cardbattle.engine.result.Rejection;
import com.cardbattle.engine.rules.ChainOutcome;
import com.cardbattle.engine.rules.ConditionEvaluator;
import com.cardbattle.engine.rules.Passives;
import com.cardbattle.engine.rules.PlayabilityChecker;
import com.cardbattle.engine.rules.Statuses;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.rules.TurnResolver;
import com.cardbattle.engine.state.ExtraPlayState;
import com.cardbattle.engine.state.FieldCard;
import com.cardbattle.engine.state.GameRng;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;
import com.cardbattle.engine.state.StatusState;
import com.cardbattle.engine.view.CardView;
import com.cardbattle.engine.view.GameSnapshot;
import com.cardbattle.engine.view.PlayerView;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final Clock clock;

    /** 실제 서버용: 시스템 시계, 차례 전환 시간 {@link GameSettings#TURN_TRANSITION_MS} */
    public GameEngine(CardPack pack) {
        this(pack, EffectRegistry.defaults(), Clock.systemUTC(), GameSettings.TURN_TRANSITION_MS);
    }

    /**
     * @param turnTransitionMs 차례 전환 시간 (FR-GAME-10). 시계를 고정하는 테스트는 0을 넘긴다
     */
    public GameEngine(CardPack pack, EffectRegistry effects, Clock clock, int turnTransitionMs) {
        this.pack = pack;
        this.effects = effects;
        this.clock = clock;
        this.playability = new PlayabilityChecker(pack);
        this.resolver = new TurnResolver(pack, effects, playability, clock, turnTransitionMs);
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
        // 멈춰! 저주: 대상을 고르는 카드의 대상은 항상 자기 자신
        boolean forcedSelf = card.targeting().requiresChoice() && Passives.has(actor, Passives.FORCE_SELF_TARGET);
        if (card.targeting().requiresChoice() && cmd.targetId() == null && !forcedSelf) {
            return ActionResult.rejected(Rejection.of(RejectCode.INVALID_TARGET, "대상을 골라야 합니다"));
        }
        Playability check = playability.check(state, actor, card, cmd.targetId());
        if (!check.playable()) {
            return ActionResult.rejected(check.toRejection());
        }
        PlayerState chosen = !card.targeting().requiresChoice() ? null
                : forcedSelf ? actor : state.player(cmd.targetId());

        EventSink events = new EventSink(state);
        markActive(actor, events);

        // 코스모의 강화기 저주: 일정 확률로 카드 내기가 버리기로 바뀐다
        Map<?, ?> fumble = Passives.find(actor, Passives.PLAY_BECOMES_DISCARD);
        if (fumble != null && !(card.alwaysPlayable() && Boolean.TRUE.equals(fumble.get("exceptAlwaysPlayable")))
                && GameRng.nextInt(state, 1_000_000) < Math.round(Passives.decimal(fumble, "p", 0) * 1_000_000)) {
            events.toAll(EventType.PLAY_FUMBLED, payload(
                    "playerId", actor.getPlayerId(), "cardId", card.id(), "instanceId", inst.instanceId()));
            return discardInstance(state, actor, inst, events);
        }

        actor.getHand().remove(inst);
        ExtraPlayState combo = state.getExtraPlay();
        String mode = combo == null ? null : combo.getMode();
        int attack = card.attackCard() ? attackFor(state, actor, card) : 0;
        if ("DOUBLE".equals(mode) && card.attackCard() && !combo.getNoDoubleCardIds().contains(card.id())) {
            attack *= 2; // 슈퍼파워
        }
        TurnContext ctx = new TurnContext(state, pack, actor, card, attack, chosen, events);
        ctx.setCardInstanceId(inst.instanceId());
        restoreCombo(ctx, combo);
        StatusState bonus = actor.status(Statuses.NEXT_ATTACK_BONUS);
        if (card.attackCard() && bonus != null && bonus.getAppliedTurn() < state.getTurnNumber()) {
            ctx.removeStatus(actor, bonus); // 스팀팩 보너스는 공격 카드 한 장에 한 번 쓰인다
        }

        events.toAll(EventType.CARD_PLAYED, payload(
                "playerId", actor.getPlayerId(),
                "cardId", card.id(),
                "instanceId", inst.instanceId(),
                "attack", card.attackCard() ? attack : null,
                "targetId", chosen == null ? null : chosen.getPlayerId()));

        // 효과와 조건은 "직전 필드"를 본다. 필드 교체는 판정 뒤에 한다 (7.7)
        effects.run(ctx, ctx.cardEffects(Timing.ON_PLAY));
        if ("HEAL_SELF".equals(mode) && card.attackCard()) {
            ctx.heal(actor, ctx.attack(), "CONSUME"); // 컨슘: 공격력만큼 회복하고, 이 카드는 공격으로 치지 않는다
            ctx.setAttackCounts(false);
        }

        // 추가 제출이 있었다면 지금까지 낸 카드를 모아 한 번에 판정한다
        int total = (combo == null ? 0 : combo.getAttack()) + (ctx.attackCounts() ? ctx.attack() : 0);
        boolean attackPlayed = (combo != null && combo.isAttackPlayed()) || ctx.attackCounts();
        List<FieldCard> played = new ArrayList<>(combo == null ? List.of() : combo.getPlayed());
        played.add(new FieldCard(inst.instanceId(), card.id(), actor.getPlayerId(), attack, state.getTurnNumber()));
        played.addAll(ctx.extraFieldCards());

        if (ctx.extraRequest() != null && startExtraPlay(state, actor, ctx, played, total, attackPlayed, events)) {
            return ActionResult.accepted(events.commit());
        }
        state.setExtraPlay(null);
        ctx.setAttack(total);
        ctx.setAttackCounts(attackPlayed);
        return finishTurn(state, ctx, played, events);
    }

    /** 누적 판정 → 필드 교체 → 정산. 필드는 이번 턴에 낸 카드들로 바뀌고, 그 카드들이 건 락만 남는다 */
    private ActionResult finishTurn(GameState state, TurnContext ctx, List<FieldCard> played, EventSink events) {
        resolver.judge(ctx);
        if (!played.isEmpty()) {
            state.setField(played);
            events.toAll(EventType.FIELD_CHANGED, payload("field", List.copyOf(state.getField())));
            List<String> ids = played.stream().map(FieldCard::instanceId).toList();
            if (state.getFieldLocks().removeIf(l -> !ids.contains(l.getSourceInstanceId()))) {
                events.toAll(EventType.FIELD_LOCKS_CHANGED, payload("locks", List.copyOf(state.getFieldLocks())));
            }
        }
        resolver.settle(ctx);
        return ActionResult.accepted(events.commit());
    }

    /**
     * 추가 제출 상태로 들어간다 (턴은 끝나지 않고 타이머도 그대로). 낼 수 있는 카드가 없으면
     * discardIfNone일 때는 버리기만 할 수 있는 상태가 되고, 아니면 추가 제출 없이 끝낸다(false).
     */
    @SuppressWarnings("unchecked")
    private boolean startExtraPlay(GameState state, PlayerState actor, TurnContext ctx, List<FieldCard> played,
                                   int total, boolean attackPlayed, EventSink events) {
        EffectSpec req = ctx.extraRequest();
        ExtraPlayState next = new ExtraPlayState();
        next.setPlayed(played);
        next.setAttack(total);
        next.setAttackPlayed(attackPlayed);
        next.setImmune(ctx.immune());
        next.setOutcome(ctx.outcome() == null ? null : ctx.outcome().name());
        next.setMode(req.str("attackMode", "ANY"));
        next.setFilter(req.raw("filter") instanceof Map<?, ?> f ? (Map<String, Object>) f : null);
        next.setNoDoubleCardIds(req.raw("noDoubleCardIds") instanceof List<?> ids
                ? ids.stream().map(String::valueOf).toList() : List.of());
        next.setLastCardId(ctx.card().id());
        next.setLastInstanceId(ctx.cardInstanceId());
        next.setChosenTargetId(ctx.chosenTarget() == null ? null : ctx.chosenTarget().getPlayerId());
        state.setExtraPlay(next);

        boolean canPlay = actor.getHand().stream()
                .anyMatch(c -> playability.check(state, actor, requireCard(c), null).playable());
        if (!canPlay) {
            if (!req.bool("discardIfNone", false) || actor.getHand().isEmpty()) {
                state.setExtraPlay(null);
                return false;
            }
            next.setDiscardOnly(true);
        }
        events.toAll(EventType.EXTRA_PLAY_STARTED, payload(
                "playerId", actor.getPlayerId(), "mode", next.getMode(), "filter", next.getFilter(),
                "discardOnly", next.isDiscardOnly()));
        events.toPlayer(actor.getPlayerId(), EventType.PLAYABILITY_UPDATED,
                payload("cards", resolver.playabilityOf(state, actor)));
        return true;
    }

    /** 추가 제출 중이던 면역·누적 판정 결과를 이어받는다 */
    private static void restoreCombo(TurnContext ctx, ExtraPlayState combo) {
        if (combo == null) {
            return;
        }
        if (combo.isImmune()) {
            ctx.grantImmunity();
        }
        if (combo.getOutcome() != null) {
            ctx.decideOutcome(ChainOutcome.valueOf(combo.getOutcome()));
        }
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
        markActive(actor, events);
        return discardInstance(state, actor, inst, events);
    }

    /** 직접 행동했으니 연속 시간 초과를 초기화한다. 자리 비움이었다면 표시를 끈다 */
    private static void markActive(PlayerState actor, EventSink events) {
        if (actor.away()) {
            events.toAll(EventType.PLAYER_AWAY_CHANGED, payload("playerId", actor.getPlayerId(), "away", false));
        }
        actor.setConsecutiveTimeouts(0);
    }

    private ActionResult discardInstance(GameState state, PlayerState actor, CardInstance inst, EventSink events) {
        actor.getHand().remove(inst);
        events.toAll(EventType.CARD_DISCARDED, payload(
                "playerId", actor.getPlayerId(), "cardId", inst.cardId(), "instanceId", inst.instanceId()));
        return endWithoutPlay(state, actor, events);
    }

    /**
     * 카드를 내지 않고 턴을 끝낸다 (버리기·시간 초과). 추가 제출 중이었다면 그때까지 낸 카드로 판정한다.
     */
    private ActionResult endWithoutPlay(GameState state, PlayerState actor, EventSink events) {
        ExtraPlayState combo = state.getExtraPlay();
        if (combo == null) {
            TurnContext ctx = new TurnContext(state, pack, actor, null, 0, null, events);
            resolver.judge(ctx);
            resolver.settle(ctx);
            return ActionResult.accepted(events.commit());
        }
        state.setExtraPlay(null);
        PlayerState chosen = combo.getChosenTargetId() == null ? null : state.player(combo.getChosenTargetId());
        TurnContext ctx = new TurnContext(state, pack, actor, pack.card(combo.getLastCardId()), combo.getAttack(),
                chosen, events);
        ctx.setCardInstanceId(combo.getLastInstanceId());
        restoreCombo(ctx, combo);
        ctx.setAttackCounts(combo.isAttackPlayed());
        return finishTurn(state, ctx, combo.getPlayed(), events);
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
        if (actor.getConsecutiveTimeouts() == GameSettings.AWAY_AFTER_TIMEOUTS) {
            events.toAll(EventType.PLAYER_AWAY_CHANGED, payload("playerId", actor.getPlayerId(), "away", true));
        }
        return endWithoutPlay(state, actor, events);
    }

    // ------------------------------------------------------------------
    // 강퇴 — 자리 비움인 플레이어만 (PRD 4.3, FR-GAME-07). 누가 방장인지는 서버가 확인한다
    // ------------------------------------------------------------------

    public ActionResult kick(GameState state, String playerId) {
        if (!state.inProgress()) {
            return ActionResult.rejected(Rejection.of(RejectCode.GAME_FINISHED, "이미 끝난 게임입니다"));
        }
        PlayerState target = state.player(playerId);
        if (target == null || !target.alive()) {
            return ActionResult.rejected(Rejection.of(RejectCode.PLAYER_NOT_FOUND, "게임 중인 플레이어가 아닙니다"));
        }
        if (!target.away()) {
            return ActionResult.rejected(Rejection.of(RejectCode.PLAYER_NOT_AWAY,
                    "자리 비움(" + GameSettings.AWAY_AFTER_TIMEOUTS + "번 연속 시간 초과)인 플레이어만 강퇴할 수 있습니다"));
        }
        EventSink events = new EventSink(state);
        resolver.forfeit(state, target, events);
        return ActionResult.accepted(events.commit());
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    /** 스냅샷에 넣는 최근 공개 이벤트 수 (게임 로그 복원용) */
    public static final int RECENT_EVENT_LIMIT = 200;

    public GameSnapshot snapshot(GameState state, String viewerId) {
        return snapshot(state, viewerId, List.of());
    }

    /**
     * 특정 플레이어 시점의 스냅샷 (재접속용). 다른 사람의 손패는 넣지 않는다.
     *
     * @param history 지금까지 기록된 이벤트 (오래된 것부터). 이 중 공개 이벤트의 최근 것만 담는다
     *                — 개인 이벤트(남의 손패 등)는 절대 넣지 않는다
     */
    public GameSnapshot snapshot(GameState state, String viewerId, List<GameEvent> history) {
        long lastSeq = state.getNextSeq() - 1;
        List<GameEvent> recent = history.stream()
                .filter(e -> e.publicEvent() && e.seq() <= lastSeq)
                .toList();
        recent = recent.subList(Math.max(0, recent.size() - RECENT_EVENT_LIMIT), recent.size());
        PlayerState viewer = state.player(viewerId);
        List<CardInstance> myHand = viewer == null ? List.of() : List.copyOf(viewer.getHand());
        boolean myTurn = viewer != null && state.inProgress() && viewer == state.currentPlayer();
        return new GameSnapshot(
                state.getGameId(),
                state.getVersion(),
                lastSeq,
                state.getSettings(),
                state.getStatus(),
                state.getTurnNumber(),
                state.currentPlayer().getPlayerId(),
                state.getDirection(),
                state.getCurrentAttack(),
                state.getAccumulatedDamage(),
                List.copyOf(state.getField()),
                List.copyOf(state.getFieldLocks()),
                state.getTimeBomb(),
                state.getDrawCountdown(),
                state.getExtraPlay(),
                playerViews(state),
                viewerId,
                myHand,
                myTurn ? resolver.playabilityOf(state, viewer) : List.of(),
                state.getTurnDeadlineEpochMs(),
                state.getTurnActiveFromEpochMs(),
                Math.max(0, state.getTurnActiveFromEpochMs() - clock.millis()),
                List.copyOf(state.getWinnerIds()),
                pack.code(),
                pack.version(),
                pack.cards().stream().map(CardView::of).toList(),
                List.copyOf(recent));
    }

    private static List<PlayerView> playerViews(GameState state) {
        return state.getPlayers().stream()
                .map(p -> new PlayerView(p.getPlayerId(), p.getNickname(), p.getSeat(), p.getHp(),
                        Passives.hpCap(p), p.getHand().size(), Passives.handLimit(p), p.isEliminated(),
                        p.cursed() ? new PlayerView.Curse(p.getCurse().getCardId(), p.getCurse().getCasterId()) : null,
                        p.getStatuses().stream().map(st -> new PlayerView.Status(st.getStatus(), st.getTurnsLeft()))
                                .toList(),
                        Passives.has(p, Passives.REVEAL_HAND) ? List.copyOf(p.getHand()) : null,
                        p.away()))
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
        if (clock.millis() < state.getTurnActiveFromEpochMs()) {
            return Rejection.of(RejectCode.TURN_TRANSITION, "차례가 넘어오는 중입니다. 잠시 후에 낼 수 있습니다");
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

    /**
     * 이번에 낼 공격 카드의 실제 공격력. 랜덤 공격력을 굴리고 CONDITIONAL_ATTACK을 반영한다.
     * 필드를 교체하기 전에 부르므로 조건은 직전 필드를 본다 (D12).
     */
    private int attackFor(GameState state, PlayerState actor, CardDefinition card) {
        int attack = card.randomAttack()
                ? GameRng.between(state, card.attackMin(), card.attackMax())
                : card.attack();
        if (!Passives.has(actor, Passives.STRIP_ATTACK_EFFECTS)) {
            ConditionEvaluator conditions = new ConditionEvaluator(pack);
            for (EffectSpec e : card.effectsAt(Timing.ON_PLAY)) {
                if ("CONDITIONAL_ATTACK".equals(e.type())
                        && conditions.test(PackParser.condition(e.raw("condition"), "condition"), state, actor)) {
                    attack = ConditionalAttackEffect.adjust(attack, e);
                }
            }
        }
        StatusState bonus = actor.status(Statuses.NEXT_ATTACK_BONUS);
        if (bonus != null && bonus.getAppliedTurn() < state.getTurnNumber()
                && bonus.getParams().get("value") instanceof Number n) {
            attack += n.intValue();
        }
        return attack;
    }
}
