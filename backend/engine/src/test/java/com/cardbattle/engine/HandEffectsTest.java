package com.cardbattle.engine;

import com.cardbattle.engine.card.CardCategory;
import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.card.Targeting;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.pack.PackValidator;
import com.cardbattle.engine.state.FieldCard;
import com.cardbattle.engine.state.FieldLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.cardbattle.engine.TestCards.attack;
import static com.cardbattle.engine.TestCards.card;
import static com.cardbattle.engine.TestCards.effect;
import static com.cardbattle.engine.TestCards.pack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 2 손패 효과: 교체, 교환, 카드 지급, 필드에서 가져오기 */
class HandEffectsTest {

    private static CardDefinition misc(String id, Targeting targeting, EffectSpec... e) {
        return card(id, CardCategory.MISC, targeting, List.of(), e);
    }

    private static CardPack testPack() {
        return pack(
                attack("t.a10", 10),
                attack("t.axe", 25, effect("HEAL", "target", "SELF", "amount", 10)),
                attack("t.water", 10, effect("FIELD_LOCK", "filter", Map.of("category", "ATTACK"), "turns", 2)),
                misc("t.flip", Targeting.NONE, effect("REPLACE_HAND", "target", "ALL")),
                misc("t.sue", Targeting.CHOSEN_ANY, effect("REPLACE_HAND", "target", "CHOSEN", "resetHandLimit", true)),
                attack("t.hip", 20, effect("REPLACE_HAND", "target", "SELF", "count", 1, "position", "LEFTMOST")),
                misc("t.spoon", Targeting.NONE, effect("REPLACE_HAND", "target", "SELF",
                        "cardIds", List.of("t.axe", "t.axe", "t.axe", "t.axe", "t.axe"), "resetHandLimit", true)),
                misc("t.plunder", Targeting.CHOSEN_OTHER, effect("SWAP_HAND", "target", "CHOSEN",
                        "includeHandLimit", true)),
                card("t.santa", CardCategory.BENEFIT, Targeting.NONE, List.of(),
                        effect("GIVE_CARDS", "target", "SELF", "cardIds", List.of("t.water"))),
                misc("t.trainer", Targeting.NONE, effect("RETRIEVE_FROM_FIELD", "position", "LEFTMOST")));
    }

    private static TestGame game(String... players) {
        return TestGame.with(testPack(), players);
    }

    private static Set<String> ids(List<CardInstance> hand) {
        return hand.stream().map(CardInstance::instanceId).collect(Collectors.toSet());
    }

    private static void fill(TestGame g, String player, int n) {
        for (int i = 0; i < n; i++) {
            g.give(player, "t.a10");
        }
    }

    @Test
    @DisplayName("밥상 뒤집기: 모든 사람의 손패가 새 카드로 바뀐다")
    void flipTable() {
        TestGame g = game("p1", "p2", "p3");
        fill(g, "p2", 5);
        fill(g, "p3", 5);
        Set<String> before2 = ids(g.p("p2").getHand());
        g.play("p1", g.give("p1", "t.flip"));
        assertEquals(5, g.p("p2").getHand().size());
        assertTrue(ids(g.p("p2").getHand()).stream().noneMatch(before2::contains));
        assertEquals(5, g.p("p1").getHand().size());
    }

    @Test
    @DisplayName("고소: 고른 사람의 손패를 바꾸고 늘어난 손패 한도도 초기화한다")
    void lawsuit() {
        TestGame g = game("p1", "p2");
        g.p("p2").setHandLimit(7);
        fill(g, "p2", 7);
        g.play("p1", g.give("p1", "t.sue"), "p2");
        assertEquals(5, g.p("p2").getHandLimit());
        assertEquals(5, g.p("p2").getHand().size());
    }

    @Test
    @DisplayName("힙통령: 손패 가장 왼쪽 한 장만 새 카드로 바꾼다")
    void hipLeader() {
        TestGame g = game("p1", "p2");
        String left = g.give("p1", "t.a10");
        String keep = g.give("p1", "t.a10");
        g.play("p1", g.give("p1", "t.hip"));
        Set<String> hand = ids(g.p("p1").getHand());
        assertFalse(hand.contains(left));
        assertTrue(hand.contains(keep));
    }

    @Test
    @DisplayName("금수저: 손패를 금도끼 5장으로 바꾸고 손패 한도를 초기화한다")
    void goldSpoon() {
        TestGame g = game("p1", "p2");
        g.p("p1").setHandLimit(6);
        g.play("p1", g.give("p1", "t.spoon"));
        assertEquals(5, g.p("p1").getHandLimit());
        assertTrue(g.p("p1").getHand().stream().allMatch(c -> c.cardId().equals("t.axe")));
        assertEquals(5, g.p("p1").getHand().size());
    }

    @Test
    @DisplayName("약탈의 수행사제: 고른 사람과 손패·손패 한도를 맞바꾼다")
    void plunder() {
        TestGame g = game("p1", "p2");
        g.p("p2").setHandLimit(6);
        fill(g, "p2", 6);
        Set<String> theirs = ids(g.p("p2").getHand());
        fill(g, "p1", 4);
        g.play("p1", g.give("p1", "t.plunder"), "p2");
        assertTrue(ids(g.p("p1").getHand()).containsAll(theirs));
        assertEquals(6, g.p("p1").getHandLimit());
        assertEquals(5, g.p("p2").getHandLimit());
    }

    @Test
    @DisplayName("산타의 선물: 정해진 카드를 손패에 받는다 (한도를 넘어도 들어간다)")
    void giveCards() {
        TestGame g = game("p1", "p2");
        fill(g, "p1", 5);
        g.play("p1", g.give("p1", "t.santa"));
        assertEquals(1, g.p("p1").getHand().stream().filter(c -> c.cardId().equals("t.water")).count());
        assertEquals(6, g.p("p1").getHand().size());
    }

    @Test
    @DisplayName("조교: 직전 필드 카드를 손패로 가져오고, 그 카드의 락은 풀린다")
    void retrieveFromField() {
        TestGame g = game("p1", "p2");
        g.state.setField(List.of(new FieldCard("f9", "t.water", "p2", 10, 0)));
        g.state.getFieldLocks().add(new FieldLock("f9", "t.water", "p2", Map.of("category", "ATTACK"), 99));
        g.play("p1", g.give("p1", "t.trainer"));
        assertTrue(g.p("p1").getHand().stream().anyMatch(c -> c.cardId().equals("t.water")));
        assertTrue(g.state.getFieldLocks().isEmpty());
        assertEquals("t.trainer", g.state.getField().get(0).cardId());
    }

    @Test
    @DisplayName("팩 검증: 없는 카드 ID와 잘못된 위치를 거부한다")
    void validation() {
        PackValidator validator = new PackValidator(EffectRegistry.defaults());
        assertTrue(validator.validate(testPack()).isEmpty(), validator.validate(testPack()).toString());
        List<String> errors = validator.validate(pack(
                misc("t.bad", Targeting.NONE, effect("GIVE_CARDS", "target", "SELF", "cardIds", List.of("t.nope"))),
                misc("t.bad2", Targeting.NONE, effect("RETRIEVE_FROM_FIELD", "position", "MIDDLE"))));
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.bad.effects[0].cardIds")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.bad2.effects[0].position")), errors.toString());
    }
}
