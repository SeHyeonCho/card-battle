package com.cardbattle.engine;

import com.cardbattle.engine.card.CardCategory;
import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.ConditionSpec;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.card.Targeting;
import com.cardbattle.engine.card.Timing;

import java.util.List;
import java.util.Set;

import static com.cardbattle.engine.event.EventSink.payload;

/** 테스트용 카드와 팩. 규칙 테스트가 카드 내용에 휘둘리지 않도록 단순한 카드만 쓴다. */
final class TestCards {

    private TestCards() {
    }

    static CardDefinition attack(String id, int attack, EffectSpec... effects) {
        return new CardDefinition(id, id, CardCategory.ATTACK, null, attack, null, null, Set.of(), Targeting.NONE,
                false, List.of(), List.of(effects), "공격력 " + attack, null, 10);
    }

    static CardDefinition card(String id, CardCategory category, Targeting targeting, List<ConditionSpec> conditions,
                               EffectSpec... effects) {
        return new CardDefinition(id, id, category, null, null, null, null, Set.of(), targeting, false, conditions,
                List.of(effects), id, null, 5);
    }

    /** 태그·필드를 자유롭게 정하는 카드 */
    static CardDefinition tagged(String id, CardCategory category, Integer attack, Set<String> tags,
                                 boolean alwaysPlayable, EffectSpec... effects) {
        return new CardDefinition(id, id, category, null, attack, null, null, tags, Targeting.NONE, alwaysPlayable,
                List.of(), List.of(effects), id, null, 5);
    }

    static CardPack pack(CardDefinition... cards) {
        return new CardPack("t", "테스트 팩", 1, "PUBLIC", "SC1", List.of(cards));
    }

    static EffectSpec effect(String type, Object... keyValues) {
        return new EffectSpec(type, Timing.ON_PLAY, payload(keyValues));
    }

    static EffectSpec effectAt(Timing timing, String type, Object... keyValues) {
        return new EffectSpec(type, timing, payload(keyValues));
    }

    static ConditionSpec condition(String type, Object... keyValues) {
        return new ConditionSpec(type, payload(keyValues), List.of());
    }

    /** Phase 1 프리미티브를 모두 쓰는 기본 테스트 팩 */
    static CardPack standardPack() {
        return new CardPack("t", "테스트 팩", 1, "PUBLIC", "SC1", List.of(
                attack("t.a5", 5),
                attack("t.a10", 10),
                attack("t.a20", 20),
                attack("t.a30", 30),
                attack("t.recoil", 40, effect("DAMAGE", "target", "SELF", "amount", 10)),
                attack("t.drag", 15, effectAt(Timing.ON_RECEIVE, "MODIFY_ACCUMULATED", "op", "ADD", "value", 30)),
                new CardDefinition("t.dice", "t.dice", CardCategory.ATTACK, null, null, 5, 30, Set.of(),
                        Targeting.NONE, false, List.of(), List.of(), "5~30", null, 5),
                new CardDefinition("t.lowhp", "t.lowhp", CardCategory.ATTACK, null, 40, null, null, Set.of(),
                        Targeting.NONE, false, List.of(condition("SELF_HP_LTE", "value", 100)), List.of(),
                        "체력 100 이하일 때만", null, 3),
                card("t.reduce30", CardCategory.SUPPORT, Targeting.NONE, List.of(),
                        effect("MODIFY_ACCUMULATED", "op", "SUB", "value", 30)),
                card("t.reset", CardCategory.SUPPORT, Targeting.NONE, List.of(),
                        effect("RESET_ACCUMULATED")),
                card("t.pass", CardCategory.SUPPORT, Targeting.NONE, List.of(),
                        effect("TRANSFER_ACCUMULATED", "to", "NEXT", "multiplier", 1)),
                card("t.pass2x", CardCategory.SUPPORT, Targeting.NONE, List.of(),
                        effect("TRANSFER_ACCUMULATED", "to", "NEXT", "multiplier", 2)),
                card("t.reflect", CardCategory.SUPPORT, Targeting.NONE, List.of(),
                        effect("TRANSFER_ACCUMULATED", "to", "PREV", "immediate", true)),
                card("t.snipe", CardCategory.SUPPORT, Targeting.CHOSEN_OTHER, List.of(),
                        effect("TRANSFER_ACCUMULATED", "to", "CHOSEN", "immediate", true)),
                card("t.heal20", CardCategory.BENEFIT, Targeting.NONE, List.of(),
                        effect("HEAL", "target", "SELF", "amount", 20)),
                card("t.poke", CardCategory.BENEFIT, Targeting.CHOSEN_OTHER, List.of(),
                        effect("DAMAGE", "target", "CHOSEN", "amount", 15)),
                card("t.bomb", CardCategory.BENEFIT, Targeting.NONE, List.of(),
                        effect("DAMAGE", "target", "ALL_OTHERS", "amount", 999)),
                card("t.phoenix", CardCategory.MISC, Targeting.NONE, List.of(),
                        effect("DAMAGE", "target", "SELF", "amount", 50),
                        effectAt(Timing.ON_TURN_END, "HEAL", "target", "SELF", "amount", 50)),
                card("t.blank", CardCategory.MISC, Targeting.NONE, List.of())));
    }
}
