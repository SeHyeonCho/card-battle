package com.cardbattle.engine.rules;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.ConditionSpec;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.card.Timing;
import com.cardbattle.engine.event.EventSink;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.state.CurseState;
import com.cardbattle.engine.state.FieldCard;
import com.cardbattle.engine.state.GameRng;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;
import com.cardbattle.engine.state.StatusState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.cardbattle.engine.event.EventSink.payload;

/**
 * 한 턴(행동 하나)을 처리하는 동안의 작업 공간.
 * 효과 핸들러는 이 객체를 통해서만 상태를 바꾼다.
 */
public final class TurnContext {

    private final GameState state;
    private final CardPack pack;
    private final PlayerState actor;
    private final CardDefinition card;
    private int attack;
    /** 누적 판정에서 공격 카드로 볼 것인가. null이면 낸 카드가 공격 카드인지로 정한다 */
    private Boolean attackCounts;
    /** 이 카드가 요청한 추가 제출 (EXTRA_PLAY) */
    private EffectSpec extraRequest;
    /** 전군 돌격처럼 함께 필드에 놓일 카드 */
    private final List<FieldCard> extraFieldCards = new ArrayList<>();
    private final PlayerState chosenTarget;
    private final EventSink events;

    /** 저주 효과를 실행하는 중이면 저주를 건 사람 (CASTER 대상), 아니면 null */
    private PlayerState caster;
    private boolean curseContext;
    /** 낸 카드의 인스턴스 ID (필드 락의 출처). 버리기·저주 문맥이면 null */
    private String cardInstanceId;

    private ChainOutcome outcome;
    private int restartAttack;
    private boolean immune;
    private boolean received;

    public TurnContext(GameState state, CardPack pack, PlayerState actor, CardDefinition card, int attack,
                       PlayerState chosenTarget, EventSink events) {
        this.state = state;
        this.pack = pack;
        this.actor = actor;
        this.card = card;
        this.attack = attack;
        this.chosenTarget = chosenTarget;
        this.events = events;
    }

    /**
     * 저주 효과(onTurnEnd)를 실행할 작업 공간. 행동한 사람은 저주받은 사람이고,
     * 대상 CURSED = 저주받은 사람, CASTER = 저주를 건 사람이다. 누적 판정에는 영향을 주지 않는다.
     */
    public TurnContext forCurse(PlayerState cursed, PlayerState caster) {
        TurnContext c = new TurnContext(state, pack, cursed, null, 0, null, events);
        c.caster = caster;
        c.curseContext = true;
        return c;
    }

    public GameState state() {
        return state;
    }

    public CardPack pack() {
        return pack;
    }

    public PlayerState actor() {
        return actor;
    }

    /** 낸 카드. 버리기·시간 초과면 null */
    public CardDefinition card() {
        return card;
    }

    public String cardInstanceId() {
        return cardInstanceId;
    }

    public void setCardInstanceId(String cardInstanceId) {
        this.cardInstanceId = cardInstanceId;
    }

    public int attack() {
        return attack;
    }

    public void setAttack(int attack) {
        this.attack = attack;
    }

    public boolean attackCounts() {
        return attackCounts != null ? attackCounts : card != null && card.attackCard();
    }

    public void setAttackCounts(boolean attackCounts) {
        this.attackCounts = attackCounts;
    }

    public EffectSpec extraRequest() {
        return extraRequest;
    }

    public void requestExtraPlay(EffectSpec spec) {
        this.extraRequest = spec;
    }

    public List<FieldCard> extraFieldCards() {
        return extraFieldCards;
    }

    public void addFieldCard(FieldCard card) {
        extraFieldCards.add(card);
    }

    public PlayerState chosenTarget() {
        return chosenTarget;
    }

    public EventSink events() {
        return events;
    }

    public List<PlayerState> resolveTargets(String target) {
        if ("CURSED".equals(target)) {
            return curseContext && actor.alive() ? List.of(actor) : List.of();
        }
        if ("CASTER".equals(target)) {
            return caster != null && caster.alive() ? List.of(caster) : List.of();
        }
        return TargetResolver.resolve(target, state, actor, chosenTarget);
    }

    /** 낸 카드의 효과. 공격 카드 효과 무시 저주(적절한 카드)가 걸려 있으면 비어 있다 */
    public List<EffectSpec> cardEffects(Timing timing) {
        if (card == null || (card.attackCard() && Passives.has(actor, Passives.STRIP_ATTACK_EFFECTS))) {
            return List.of();
        }
        return card.effectsAt(timing);
    }

    /** 낸 사람 기준으로 조건을 평가한다 (효과의 when 등) */
    public boolean test(ConditionSpec condition) {
        return new ConditionEvaluator(pack).test(condition, state, actor);
    }

    public int random(int bound) {
        return GameRng.nextInt(state, bound);
    }

    /** 체력을 깎는다. 탈락 판정은 정산 단계에서 한 번에 한다 (PRD 7.8) */
    public void damage(PlayerState target, int amount, String cause) {
        if (amount <= 0 || !target.alive()) {
            return;
        }
        if ("ACCUMULATED".equals(cause) || "TRANSFER".equals(cause) || "REDIRECT".equals(cause)) {
            // 충격과 공포: 누적 데미지를 맞을 때 추가 피해
            amount += Passives.number(Passives.find(target, Passives.EXTRA_RECEIVE_DAMAGE), "value", 0);
        }
        target.setHp(target.getHp() - amount);
        events.toAll(EventType.HP_CHANGED, payload(
                "playerId", target.getPlayerId(), "hp", target.getHp(), "delta", -amount, "cause", cause));
    }

    /** 체력을 회복한다. 체력 상한을 넘지 않는다 */
    public void heal(PlayerState target, int amount, String cause) {
        if (amount <= 0 || !target.alive()) {
            return;
        }
        Map<?, ?> halve = Passives.find(target, Passives.HEAL_MULTIPLIER);
        if (halve != null && !(halve.get("exceptCategories") instanceof List<?> except
                && card != null && except.contains(card.category().name()))) {
            amount = (int) Math.floor(amount * Passives.decimal(halve, "value", 1.0));
        }
        int before = target.getHp();
        int cap = Passives.hpCap(target);
        if (amount <= 0 || before >= cap) {
            return; // 상한을 이미 넘었으면 회복으로 체력이 줄지 않게 한다 (초과분은 정산 6단계에서 정리)
        }
        int after = Math.min(cap, before + amount);
        if (after == before) {
            return;
        }
        target.setHp(after);
        events.toAll(EventType.HP_CHANGED, payload(
                "playerId", target.getPlayerId(), "hp", after, "delta", after - before, "cause", cause));
    }

    /** 체력을 정해진 값으로 만든다 (체력 상한까지). 탈락 판정은 정산에서 한다 */
    public void setHp(PlayerState target, int value, String cause) {
        if (!target.alive()) {
            return;
        }
        int before = target.getHp();
        int after = Math.min(Passives.hpCap(target), value);
        if (after == before) {
            return;
        }
        target.setHp(after);
        events.toAll(EventType.HP_CHANGED, payload(
                "playerId", target.getPlayerId(), "hp", after, "delta", after - before, "cause", cause));
    }

    /** 손패 한도를 바꾼다 (최소 1) */
    public void setHandLimit(PlayerState target, int limit) {
        int next = Math.max(1, limit);
        if (next == target.getHandLimit()) {
            return;
        }
        target.setHandLimit(next);
        events.toAll(EventType.HAND_LIMIT_CHANGED, payload("playerId", target.getPlayerId(), "handLimit", next));
    }

    /** 저주를 건다. 이미 있으면 덮어쓴다 (PRD 7.8) */
    public void applyCurse(PlayerState target, CurseState curse) {
        if (!target.alive()) {
            return;
        }
        target.setCurse(curse);
        events.toAll(EventType.CURSE_APPLIED, payload(
                "playerId", target.getPlayerId(), "curseId", curse.getCurseId(),
                "cardId", curse.getCardId(), "casterId", curse.getCasterId()));
    }

    public void removeCurse(PlayerState target, String reason) {
        if (!target.cursed()) {
            return;
        }
        String cardId = target.getCurse().getCardId();
        target.setCurse(null);
        events.toAll(EventType.CURSE_REMOVED, payload("playerId", target.getPlayerId(), "cardId", cardId,
                "reason", reason));
    }

    /** 지속 상태를 건다. 같은 상태가 있으면 새로 건 것으로 바꾼다 */
    public void applyStatus(PlayerState target, String status, int turns, Map<String, Object> params) {
        if (!target.alive()) {
            return;
        }
        target.getStatuses().removeIf(st -> st.getStatus().equals(status));
        target.getStatuses().add(new StatusState(status, turns, actor.getPlayerId(), params, state.getTurnNumber()));
        events.toAll(EventType.STATUS_APPLIED, payload(
                "playerId", target.getPlayerId(), "status", status, "turns", turns));
    }

    public void removeStatus(PlayerState target, StatusState status) {
        if (target.getStatuses().remove(status)) {
            events.toAll(EventType.STATUS_EXPIRED, payload("playerId", target.getPlayerId(),
                    "status", status.getStatus()));
        }
    }

    /** 팩에서 가중치대로 새 카드 한 장을 뽑는다 (드로우 규칙 7.9와 같은 방식) */
    public CardInstance drawRandom() {
        CardDefinition def = Draws.one(state, pack);
        return new CardInstance(state.newInstanceId(), def.id());
    }

    public CardInstance newCard(String cardId) {
        return new CardInstance(state.newInstanceId(), cardId);
    }

    /** 손패가 바뀌었음을 알린다: 본인에게는 손패 내용, 모두에게는 장 수 */
    public void handChanged(PlayerState player) {
        events.toPlayer(player.getPlayerId(), EventType.HAND_UPDATED, payload("hand", List.copyOf(player.getHand())));
        events.toAll(EventType.HAND_COUNT_CHANGED,
                payload("playerId", player.getPlayerId(), "count", player.getHand().size()));
    }

    /** 현재 공격력과 누적 데미지를 바꾸고 이벤트를 남긴다 */
    public void setChain(int currentAttack, int accumulatedDamage) {
        int a = Math.max(0, currentAttack);
        int d = Math.max(0, accumulatedDamage);
        if (a == state.getCurrentAttack() && d == state.getAccumulatedDamage()) {
            return;
        }
        state.setCurrentAttack(a);
        state.setAccumulatedDamage(d);
        events.toAll(EventType.ACCUMULATION_CHANGED, payload("currentAttack", a, "accumulatedDamage", d));
    }

    public ChainOutcome outcome() {
        return outcome;
    }

    /** 효과가 누적 판정을 직접 결정한다 (예: 데미지 전달). 기본 판정은 건너뛴다 */
    public void decideOutcome(ChainOutcome outcome) {
        this.outcome = outcome;
    }

    int restartAttack() {
        return restartAttack;
    }

    void restartWith(int attack) {
        this.outcome = ChainOutcome.RECEIVE_AND_RESTART;
        this.restartAttack = attack;
    }

    public boolean immune() {
        return immune;
    }

    /** 이번 턴 누적 데미지를 받지 않는다 (IMMUNE_THIS_TURN, Phase 2) */
    public void grantImmunity() {
        this.immune = true;
    }

    public boolean received() {
        return received;
    }

    void markReceived() {
        this.received = true;
    }
}
