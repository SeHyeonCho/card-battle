package com.cardbattle.engine;

import com.cardbattle.engine.card.CardCategory;
import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.Targeting;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.pack.PackValidator;
import com.cardbattle.engine.result.ActionResult;
import com.cardbattle.engine.result.PlayBlockReason;
import com.cardbattle.engine.result.RejectCode;
import com.cardbattle.engine.rules.CardFilter;
import com.cardbattle.engine.state.CurseState;
import com.cardbattle.engine.state.FieldLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.cardbattle.engine.TestCards.attack;
import static com.cardbattle.engine.TestCards.card;
import static com.cardbattle.engine.TestCards.condition;
import static com.cardbattle.engine.TestCards.effect;
import static com.cardbattle.engine.TestCards.pack;
import static com.cardbattle.engine.TestCards.tagged;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 2 기반: 카드 필터, 조건부 발동(when), 파라미터 검증, 새 조건, 필드 락·저주 락 */
class FoundationTest {

    private static final CardDefinition FIRE = tagged("t.fire", CardCategory.ATTACK, 10, Set.of("FIRE"), false);
    private static final CardDefinition RECOIL = attack("t.recoil", 40,
            effect("DAMAGE", "target", "SELF", "amount", 10));
    private static final CardDefinition HEALER = attack("t.healer", 20,
            effect("HEAL", "target", "SELF", "amount", 5));
    private static final CardDefinition DICE = new CardDefinition("t.dice", "t.dice", CardCategory.ATTACK, null,
            null, 5, 30, Set.of(), Targeting.NONE, false, List.of(), List.of(), "5~30", null, 5);
    /** 필드에 화염 카드가 있을 때만 누적을 없애는 카드 (냉동포장) */
    private static final CardDefinition FREEZE = attack("t.freeze", 5,
            effect("RESET_ACCUMULATED", "when", Map.of("type", "FIELD_HAS_TAG", "tag", "FIRE")));
    private static final CardDefinition SUPPORT = card("t.pass", CardCategory.SUPPORT, Targeting.NONE, List.of(),
            effect("TRANSFER_ACCUMULATED", "to", "NEXT"));
    private static final CardDefinition BAT = tagged("t.bat", CardCategory.ATTACK, 45, Set.of(), true);

    private static CardPack testPack() {
        return pack(FIRE, RECOIL, HEALER, DICE, FREEZE, SUPPORT, BAT,
                new CardDefinition("t.dark", "t.dark", CardCategory.ATTACK, null, 35, null, null, Set.of(),
                        Targeting.NONE, false, List.of(condition("SELF_CURSED")), List.of(), "저주 시", null, 5),
                new CardDefinition("t.combo", "t.combo", CardCategory.BENEFIT, null, null, null, null, Set.of(),
                        Targeting.NONE, false,
                        List.of(condition("HAND_HAS", "filter", Map.of("category", "ATTACK"), "count", 2)),
                        List.of(), "공격 카드 2장 이상", null, 5));
    }

    @Test
    @DisplayName("카드 필터: 분류·공격력·태그")
    void filterBasics() {
        assertTrue(CardFilter.matches(Map.of("category", "ATTACK", "attackGte", 30), RECOIL));
        assertFalse(CardFilter.matches(Map.of("category", "ATTACK", "attackGte", 30), FIRE));
        assertTrue(CardFilter.matches(Map.of("category", List.of("SUPPORT", "BENEFIT")), SUPPORT));
        assertTrue(CardFilter.matches(Map.of("tags", List.of("FIRE", "ELECTRIC")), FIRE));
        assertFalse(CardFilter.matches(Map.of("tags", List.of("ELECTRIC")), FIRE));
    }

    @Test
    @DisplayName("카드 필터: 반동(자기 피해)은 효과로 치지 않는다 (탈모)")
    void recoilIsNotAnEffect() {
        Map<String, Object> hasEffects = Map.of("category", "ATTACK", "hasEffects", true);
        assertFalse(CardFilter.matches(hasEffects, RECOIL));
        assertFalse(CardFilter.matches(hasEffects, DICE));
        assertTrue(CardFilter.matches(hasEffects, HEALER));
        assertTrue(CardFilter.matches(Map.of("hasEffects", List.of("HEAL", "DRAIN")), HEALER));
    }

    @Test
    @DisplayName("카드 필터: 랜덤 공격력은 최대값으로 판정한다")
    void randomAttackUsesMax() {
        assertTrue(CardFilter.matches(Map.of("attackGte", 25), DICE));
        assertFalse(CardFilter.matches(Map.of("attackLte", 20), DICE));
    }

    @Test
    @DisplayName("when: 조건을 만족할 때만 효과가 발동한다")
    void whenFiresOnlyIfConditionHolds() {
        TestGame hot = TestGame.with(testPack(), "p1", "p2").chain(30, 60).field("t.fire");
        hot.play("p1", hot.give("p1", "t.freeze"));
        assertEquals(200, hot.p("p1").getHp(), "화염 카드가 있으면 누적이 사라져 받지 않는다");

        TestGame cold = TestGame.with(testPack(), "p1", "p2").chain(30, 60).field("t.recoil");
        cold.play("p1", cold.give("p1", "t.freeze"));
        assertEquals(140, cold.p("p1").getHp(), "조건이 안 맞으면 효과가 없고 낮은 공격이라 누적을 받는다");
    }

    @Test
    @DisplayName("SELF_CURSED: 저주가 걸려 있어야 낼 수 있다")
    void selfCursedCondition() {
        TestGame g = TestGame.with(testPack(), "p1", "p2");
        String card = g.give("p1", "t.dark");
        assertEquals(PlayBlockReason.CONDITION_UNMET, g.play("p1", card).rejection().reason());

        g.p("p1").setCurse(new CurseState("curse.x", "t.x", "p2", Map.of()));
        assertTrue(g.play("p1", card).accepted());
    }

    @Test
    @DisplayName("HAND_HAS: 손패에 조건에 맞는 카드가 count장 이상 있어야 한다")
    void handHasCondition() {
        TestGame g = TestGame.with(testPack(), "p1", "p2");
        String combo = g.give("p1", "t.combo");
        g.give("p1", "t.fire");
        assertFalse(g.play("p1", combo).accepted());
        g.give("p1", "t.recoil");
        assertTrue(g.play("p1", combo).accepted());
    }

    @Test
    @DisplayName("필드 락: 필터에 맞는 카드는 막히고, 무조건 낼 수 있는 카드는 통과한다")
    void fieldLockBlocks() {
        TestGame g = TestGame.with(testPack(), "p1", "p2");
        g.state.getFieldLocks().add(new FieldLock("f1", "t.fire", "p2", Map.of("category", "ATTACK"), 5));

        ActionResult blocked = g.play("p1", g.give("p1", "t.recoil"));
        assertEquals(RejectCode.CARD_NOT_PLAYABLE, blocked.rejection().code());
        assertEquals(PlayBlockReason.FIELD_LOCK, blocked.rejection().reason());
        assertTrue(blocked.rejection().message().contains("t.fire"));

        assertTrue(g.play("p1", g.give("p1", "t.bat")).accepted());
    }

    @Test
    @DisplayName("저주 락: 저주 정의의 locks 필터에 맞는 카드는 막힌다")
    void curseLockBlocks() {
        TestGame g = TestGame.with(testPack(), "p1", "p2");
        g.p("p1").setCurse(new CurseState("curse.eye", "t.eye", "p2",
                Map.of("locks", List.of(Map.of("category", "SUPPORT")))));

        ActionResult blocked = g.play("p1", g.give("p1", "t.pass"));
        assertEquals(PlayBlockReason.CURSE_LOCK, blocked.rejection().reason());
        assertTrue(g.play("p1", g.give("p1", "t.fire")).accepted());
    }

    @Test
    @DisplayName("팩 검증: 효과에 없는 파라미터와 잘못된 when·필터는 거부한다")
    void validatorRejectsUnknownParams() {
        PackValidator validator = new PackValidator(EffectRegistry.defaults());
        CardPack bad = pack(
                attack("t.typo", 10, effect("HEAL", "target", "SELF", "amout", 5)),
                attack("t.badwhen", 10, effect("RESET_ACCUMULATED", "when", Map.of("type", "NOPE"))),
                new CardDefinition("t.badfilter", "t.badfilter", CardCategory.BENEFIT, null, null, null, null,
                        Set.of(), Targeting.NONE, false,
                        List.of(condition("HAND_HAS", "filter", Map.of("colour", "red"), "count", 1)),
                        List.of(), "x", null, 5));
        List<String> errors = validator.validate(bad);
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.typo.effects[0].amout")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.badwhen.effects[0].when.type")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.badfilter.conditions[0].filter.colour")),
                errors.toString());

        assertTrue(validator.validate(testPack()).isEmpty(), validator.validate(testPack()).toString());
    }
}
