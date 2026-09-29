package com.cardbattle.engine;

import com.cardbattle.engine.card.CardCategory;
import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.card.Targeting;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.pack.PackValidator;
import com.cardbattle.engine.state.CurseState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.cardbattle.engine.TestCards.attack;
import static com.cardbattle.engine.TestCards.card;
import static com.cardbattle.engine.TestCards.effect;
import static com.cardbattle.engine.TestCards.pack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 2 단순 효과 (PRD 8.3): 체력 조작, 흡수, 교환, 면역, 조건부 공격력, 확률, 손패 한도 */
class BasicEffectsTest {

    private static CardDefinition benefit(String id, Targeting targeting, EffectSpec... e) {
        return card(id, CardCategory.BENEFIT, targeting, List.of(), e);
    }

    private static CardPack testPack() {
        return pack(
                attack("t.a10", 10),
                attack("t.a40", 40),
                attack("t.storm", 20),
                benefit("t.reset100", Targeting.NONE, effect("SET_HP", "target", "ALL", "value", 100)),
                benefit("t.mind", Targeting.CHOSEN_OTHER, effect("SWAP_HP", "target", "CHOSEN")),
                benefit("t.loyal", Targeting.NONE, effect("EQUALIZE_HP", "target", "ALL_OTHERS")),
                benefit("t.halve", Targeting.CHOSEN_OTHER, effect("HALVE_HP", "target", "CHOSEN")),
                benefit("t.drain", Targeting.NONE, effect("DRAIN", "target", "ALL_OTHERS", "amount", 15)),
                card("t.mukbang", CardCategory.SUPPORT, Targeting.NONE, List.of(),
                        effect("ABSORB_ACCUMULATED", "as", "HEAL", "ratio", 1)),
                card("t.katsu", CardCategory.SUPPORT, Targeting.NONE, List.of(),
                        effect("SWAP_ATTACK_AND_ACCUMULATED"), effect("TRANSFER_ACCUMULATED", "to", "NEXT")),
                card("t.drink", CardCategory.SUPPORT, Targeting.NONE, List.of(),
                        effect("HEAL", "target", "SELF", "amountFrom", "CURRENT_ATTACK"),
                        effect("TRANSFER_ACCUMULATED", "to", "NEXT")),
                attack("t.psychic", 40, effect("IMMUNE_THIS_TURN")),
                attack("t.dragon", 35, effect("CONDITIONAL_ATTACK",
                        "condition", Map.of("type", "SELF_CURSED"), "set", 60)),
                attack("t.swag", 30, effect("CONDITIONAL_ATTACK",
                        "condition", Map.of("type", "FIELD_HAS_CARD", "cardId", "t.storm"), "set", 80)),
                attack("t.shoot", 15, effect("CONDITIONAL_ATTACK",
                        "condition", Map.of("type", "FIELD_HAS_CARD", "cardId", "t.storm"), "add", 20)),
                benefit("t.always", Targeting.NONE, effect("CHANCE", "p", 1.0,
                        "then", List.of(Map.of("type", "HEAL", "target", "SELF", "amount", 50)),
                        "else", List.of(Map.of("type", "DAMAGE", "target", "SELF", "amount", 50)))),
                benefit("t.never", Targeting.NONE, effect("CHANCE", "p", 0.0,
                        "then", List.of(Map.of("type", "HEAL", "target", "SELF", "amount", 50)),
                        "else", List.of(Map.of("type", "DAMAGE", "target", "SELF", "amount", 50)))),
                benefit("t.rocket", Targeting.NONE, effect("RANDOM_CHOICE", "table", List.of(
                        Map.of("weight", 1, "effects", List.of(Map.of("type", "HEAL", "target", "SELF", "amount", 10))),
                        Map.of("weight", 1, "effects", List.of(Map.of("type", "HEAL", "target", "SELF", "amount", 20))),
                        Map.of("weight", 1, "effects", List.of(Map.of("type", "HEAL", "target", "SELF", "amount", 30)))))),
                benefit("t.rich", Targeting.NONE, effect("HAND_LIMIT", "target", "SELF", "op", "ADD", "value", 1)));
    }

    private static TestGame game(String... players) {
        return TestGame.with(testPack(), players);
    }

    @Test
    @DisplayName("SET_HP: 모든 플레이어 체력을 100으로 (존나좋군?)")
    void setHp() {
        TestGame g = game("p1", "p2", "p3").hp("p2", 30).hp("p3", 400);
        g.play("p1", g.give("p1", "t.reset100"));
        assertEquals(100, g.p("p1").getHp());
        assertEquals(100, g.p("p2").getHp());
        assertEquals(100, g.p("p3").getHp());
    }

    @Test
    @DisplayName("SWAP_HP: 고른 사람과 체력을 맞바꾼다 (마인드 컨트롤)")
    void swapHp() {
        TestGame g = game("p1", "p2", "p3").hp("p1", 40).hp("p3", 350);
        g.play("p1", g.give("p1", "t.mind"), "p3");
        assertEquals(350, g.p("p1").getHp());
        assertEquals(40, g.p("p3").getHp());
        assertEquals(200, g.p("p2").getHp());
    }

    @Test
    @DisplayName("EQUALIZE_HP: 다른 사람 체력을 내 체력과 같게 (의리)")
    void equalizeHp() {
        TestGame g = game("p1", "p2", "p3").hp("p1", 20).hp("p2", 300);
        g.play("p1", g.give("p1", "t.loyal"));
        assertEquals(20, g.p("p2").getHp());
        assertEquals(20, g.p("p3").getHp());
    }

    @Test
    @DisplayName("HALVE_HP: 고른 사람 체력을 절반으로, 홀수면 내림 (반갈죽)")
    void halveHp() {
        TestGame g = game("p1", "p2").hp("p2", 201);
        g.play("p1", g.give("p1", "t.halve"), "p2");
        assertEquals(100, g.p("p2").getHp());
    }

    @Test
    @DisplayName("DRAIN: 다른 사람마다 15씩 깎고 그 합만큼 회복 (찰지구나)")
    void drain() {
        TestGame g = game("p1", "p2", "p3").hp("p1", 100);
        g.play("p1", g.give("p1", "t.drain"));
        assertEquals(185, g.p("p2").getHp());
        assertEquals(185, g.p("p3").getHp());
        assertEquals(130, g.p("p1").getHp());
    }

    @Test
    @DisplayName("ABSORB_ACCUMULATED: 누적을 받지 않고 그만큼 회복, 체인 종료 (먹방)")
    void absorb() {
        TestGame g = game("p1", "p2").chain(40, 120);
        g.play("p1", g.give("p1", "t.mukbang"));
        assertEquals(320, g.p("p1").getHp());
        assertEquals(0, g.state.getCurrentAttack());
        assertEquals(0, g.state.getAccumulatedDamage());
    }

    @Test
    @DisplayName("갈!: 현재 공격력과 누적을 바꿔 다음 사람에게 넘긴다")
    void swapAttackAndAccumulated() {
        TestGame g = game("p1", "p2").chain(20, 80);
        g.play("p1", g.give("p1", "t.katsu"));
        assertEquals(200, g.p("p1").getHp());
        assertEquals(80, g.state.getCurrentAttack());
        assertEquals(20, g.state.getAccumulatedDamage());
        assertEquals("p2", g.current());
    }

    @Test
    @DisplayName("HEAL amountFrom: 현재 공격력만큼 회복하고 누적은 넘긴다 (마시쪙)")
    void healFromCurrentAttack() {
        TestGame g = game("p1", "p2").chain(30, 90).hp("p1", 100);
        g.play("p1", g.give("p1", "t.drink"));
        assertEquals(130, g.p("p1").getHp());
        assertEquals(90, g.state.getAccumulatedDamage());
        assertEquals("p2", g.current());
    }

    @Test
    @DisplayName("IMMUNE_THIS_TURN: 낮은 공격이어도 누적을 받지 않고 새 체인이 시작된다 (초능력자)")
    void immunity() {
        TestGame g = game("p1", "p2").chain(50, 150);
        g.play("p1", g.give("p1", "t.psychic"));
        assertEquals(200, g.p("p1").getHp());
        assertEquals(40, g.state.getCurrentAttack());
        assertEquals(40, g.state.getAccumulatedDamage());
    }

    @Test
    @DisplayName("CONDITIONAL_ATTACK set: 저주가 걸려 있으면 공격력 60 (왼팔의 흑염룡)")
    void conditionalAttackSet() {
        TestGame plain = game("p1", "p2").chain(10, 10);
        plain.play("p1", plain.give("p1", "t.dragon"));
        assertEquals(45, plain.state.getAccumulatedDamage());

        TestGame cursed = game("p1", "p2").chain(10, 10);
        cursed.p("p1").setCurse(new CurseState("curse.x", "t.x", "p2", Map.of()));
        cursed.play("p1", cursed.give("p1", "t.dragon"));
        assertEquals(60, cursed.state.getCurrentAttack());
        assertEquals(70, cursed.state.getAccumulatedDamage());
        assertEquals(60, cursed.events(EventType.CARD_PLAYED).get(0).payload().get("attack"),
                "화면에 보이는 공격력도 바뀐 값이어야 한다");
        assertEquals(60, cursed.state.getField().get(0).attack());
    }

    @Test
    @DisplayName("CONDITIONAL_ATTACK: 조건은 직전 필드를 본다 (간지폭풍 80, 사격 +20)")
    void conditionalAttackLooksAtPreviousField() {
        TestGame g = game("p1", "p2").chain(20, 20).field("t.storm");
        g.play("p1", g.give("p1", "t.swag"));
        assertEquals(80, g.state.getCurrentAttack());

        TestGame s = game("p1", "p2").chain(20, 20).field("t.storm");
        s.play("p1", s.give("p1", "t.shoot"));
        assertEquals(35, s.state.getCurrentAttack());
    }

    @Test
    @DisplayName("CHANCE: p=1이면 then, p=0이면 else")
    void chance() {
        TestGame yes = game("p1", "p2").hp("p1", 100);
        yes.play("p1", yes.give("p1", "t.always"));
        assertEquals(150, yes.p("p1").getHp());

        TestGame no = game("p1", "p2").hp("p1", 100);
        no.play("p1", no.give("p1", "t.never"));
        assertEquals(50, no.p("p1").getHp());
    }

    @Test
    @DisplayName("RANDOM_CHOICE: 표의 항목 중 하나만 실행되고, 여러 번 하면 모든 항목이 나온다")
    void randomChoice() {
        Set<Integer> seen = new HashSet<>();
        for (long seed = 1; seed <= 60; seed++) {
            TestGame g = game("p1", "p2").hp("p1", 100);
            g.state.setRngState(seed);
            g.play("p1", g.give("p1", "t.rocket"));
            int healed = g.p("p1").getHp() - 100;
            assertTrue(Set.of(10, 20, 30).contains(healed), "회복량 " + healed);
            seen.add(healed);
        }
        assertEquals(Set.of(10, 20, 30), seen);
    }

    @Test
    @DisplayName("HAND_LIMIT: 손패 한도 +1, 정산 드로우에서 6장까지 채운다 (카드부자)")
    void handLimit() {
        TestGame g = game("p1", "p2");
        g.play("p1", g.give("p1", "t.rich"));
        assertEquals(6, g.p("p1").getHandLimit());
        assertEquals(6, g.p("p1").getHand().size());
        assertEquals(1, g.events(EventType.HAND_LIMIT_CHANGED).size());
    }

    @Test
    @DisplayName("팩 검증: 중첩 효과까지 검사하고, 중첩 효과의 CHOSEN도 대상 지정으로 인정한다")
    void nestedValidation() {
        PackValidator validator = new PackValidator(EffectRegistry.defaults());
        assertTrue(validator.validate(testPack()).isEmpty(), validator.validate(testPack()).toString());

        CardPack bad = pack(
                benefit("t.badchance", Targeting.NONE, effect("CHANCE", "p", 1.5,
                        "then", List.of(Map.of("type", "HEAL", "target", "SELF", "amout", 5)))),
                benefit("t.badtable", Targeting.NONE, effect("RANDOM_CHOICE", "table", List.of(
                        Map.of("weight", 0, "effects", List.of())))),
                attack("t.both", 10, effect("CONDITIONAL_ATTACK",
                        "condition", Map.of("type", "SELF_CURSED"), "set", 60, "add", 5)),
                benefit("t.nestedchosen", Targeting.CHOSEN_OTHER, effect("CHANCE", "p", 0.5,
                        "then", List.of(Map.of("type", "DAMAGE", "target", "CHOSEN", "amount", 50)))));
        List<String> errors = validator.validate(bad);
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.badchance.effects[0].p")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.badchance.effects[0].then[0].amout")),
                errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.badtable.effects[0].table[0].weight")),
                errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.both.effects[0]: set 또는 add")), errors.toString());
        assertTrue(errors.stream().noneMatch(e -> e.startsWith("t.nestedchosen")), errors.toString());
    }
}
