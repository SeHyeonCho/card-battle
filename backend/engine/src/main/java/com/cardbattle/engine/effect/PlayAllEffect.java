package com.cardbattle.engine.effect;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.card.Timing;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.rules.CardFilter;
import com.cardbattle.engine.rules.TurnContext;
import com.cardbattle.engine.state.FieldCard;
import com.cardbattle.engine.state.GameRng;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.cardbattle.engine.event.EventSink.payload;

/**
 * 손패에서 필터에 맞는 카드를 전부 내고, 공격력은 그 합이 된다 (전군 돌격!).
 * <pre>{ "type": "PLAY_ALL", "filter": { "category": "ATTACK" }, "suppressOnPlayEffects": true, "randomAttackAs": 15 }</pre>
 * suppressOnPlayEffects면 낸 카드들의 제출 효과는 발동하지 않지만, 필드에 있을 때의 효과(FIELD_LOCK)는 남는다.
 * randomAttackAs가 있으면 랜덤 공격력 카드는 그 값으로 고정된다 (주사위 폭탄 → 15).
 */
public final class PlayAllEffect implements EffectHandler, NestedEffects {

    private EffectRegistry registry;

    @Override
    public void bind(EffectRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String type() {
        return "PLAY_ALL";
    }

    @Override
    public Set<String> params() {
        return Set.of("filter", "suppressOnPlayEffects", "randomAttackAs");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void apply(TurnContext ctx, EffectSpec spec) {
        PlayerState actor = ctx.actor();
        Map<String, Object> filter = (Map<String, Object>) spec.raw("filter");
        boolean suppress = spec.bool("suppressOnPlayEffects", false);
        Integer randomAs = spec.integer("randomAttackAs");
        int sum = 0;
        boolean any = false;
        for (CardInstance inst : List.copyOf(actor.getHand())) {
            CardDefinition def = ctx.pack().card(inst.cardId());
            if (!CardFilter.matches(filter, def)) {
                continue;
            }
            actor.getHand().remove(inst);
            int attack = !def.attackCard() ? 0
                    : !def.randomAttack() ? def.attack()
                    : randomAs != null ? randomAs
                    : GameRng.between(ctx.state(), def.attackMin(), def.attackMax());
            ctx.events().toAll(EventType.CARD_PLAYED, payload(
                    "playerId", actor.getPlayerId(), "cardId", def.id(), "instanceId", inst.instanceId(),
                    "attack", def.attackCard() ? attack : null, "targetId", null, "via", type()));
            TurnContext sub = new TurnContext(ctx.state(), ctx.pack(), actor, def, attack, null, ctx.events());
            sub.setCardInstanceId(inst.instanceId());
            List<EffectSpec> onPlay = sub.cardEffects(Timing.ON_PLAY).stream()
                    .filter(e -> !suppress || "FIELD_LOCK".equals(e.type()))
                    .toList();
            registry.run(sub, onPlay);
            ctx.addFieldCard(new FieldCard(inst.instanceId(), def.id(), actor.getPlayerId(), attack,
                    ctx.state().getTurnNumber()));
            if (def.attackCard()) {
                sum += attack;
                any = true;
            }
        }
        if (any) {
            ctx.setAttack(sum);
            ctx.setAttackCounts(true);
        }
        ctx.handChanged(actor);
    }

    @Override
    public List<String> validate(EffectSpec spec, String path, PackCheck check) {
        List<String> errors = new ArrayList<>();
        errors.addAll(check.filter(spec.raw("filter"), path + ".filter"));
        if (spec.raw("randomAttackAs") != null) {
            Validations.requirePositiveInt(spec, "randomAttackAs", path, errors);
        }
        return errors;
    }
}
