package com.cardbattle.engine.rules;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.event.EventSink;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.state.GameRng;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;

import java.util.List;

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
    private final int attack;
    private final PlayerState chosenTarget;
    private final EventSink events;

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

    public int attack() {
        return attack;
    }

    public PlayerState chosenTarget() {
        return chosenTarget;
    }

    public EventSink events() {
        return events;
    }

    public List<PlayerState> resolveTargets(String target) {
        return TargetResolver.resolve(target, state, actor, chosenTarget);
    }

    public int random(int bound) {
        return GameRng.nextInt(state, bound);
    }

    /** 체력을 깎는다. 탈락 판정은 정산 단계에서 한 번에 한다 (PRD 7.8) */
    public void damage(PlayerState target, int amount, String cause) {
        if (amount <= 0 || !target.alive()) {
            return;
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
        int before = target.getHp();
        int after = Math.min(target.getHpCap(), before + amount);
        if (after == before) {
            return;
        }
        target.setHp(after);
        events.toAll(EventType.HP_CHANGED, payload(
                "playerId", target.getPlayerId(), "hp", after, "delta", after - before, "cause", cause));
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
