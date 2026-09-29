package com.cardbattle.engine;

import com.cardbattle.engine.card.CardCategory;
import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.card.Targeting;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.pack.PackValidator;
import com.cardbattle.engine.rules.Draws;
import com.cardbattle.engine.state.CurseState;
import com.cardbattle.engine.state.FieldCard;
import com.cardbattle.engine.state.FieldLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.cardbattle.engine.TestCards.attack;
import static com.cardbattle.engine.TestCards.card;
import static com.cardbattle.engine.TestCards.effect;
import static com.cardbattle.engine.TestCards.pack;
import static com.cardbattle.engine.TestCards.tagged;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 2 특수 카드 (CUSTOM): 연설, 야구빠따, 유성우, 본진이 바뀐다, 안아줘요, 블랙홀 */
class SpecialCardsTest {

    private static CardDefinition misc(String id, EffectSpec... e) {
        return card(id, CardCategory.MISC, Targeting.NONE, List.of(), e);
    }

    private static EffectSpec custom(String handler, Object... kv) {
        Object[] all = new Object[kv.length + 2];
        all[0] = "handler";
        all[1] = handler;
        System.arraycopy(kv, 0, all, 2, kv.length);
        return effect("CUSTOM", all);
    }

    private static CardPack testPack() {
        return pack(
                attack("t.a10", 10),
                attack("t.punch", 100),
                attack("t.storm", 20),
                card("t.snipe", CardCategory.SUPPORT, Targeting.CHOSEN_OTHER, List.of(),
                        effect("TRANSFER_ACCUMULATED", "to", "CHOSEN", "immediate", true)),
                card("t.noway", CardCategory.SUPPORT, Targeting.NONE, List.of(),
                        effect("TRANSFER_ACCUMULATED", "to", "PREV", "immediate", true)),
                card("t.lego", CardCategory.CURSE, Targeting.CHOSEN_ANY, List.of(),
                        effect("APPLY_CURSE", "target", "CHOSEN", "curse", Map.of("id", "curse.lego",
                                "locks", List.of(Map.of("category", "ATTACK"))))),
                card("t.speech", CardCategory.BENEFIT, Targeting.NONE, List.of(),
                        custom("SPEECH", "handLimit", 5, "picks", List.of(List.of("t.storm"), List.of("t.punch"),
                                List.of("t.snipe", "t.noway"), List.of("RANDOM")))),
                new CardDefinition("t.bat", "t.bat", CardCategory.ATTACK, null, 45, null, null, Set.of(),
                        Targeting.NONE, true, List.of(), List.of(custom("BASEBALL_BAT", "damage", 45)), "빠따", null, 5),
                misc("t.meteor", custom("CLEAR_FIELD"), effect("REPLACE_HAND", "target", "ALL"),
                        effect("REMOVE_CURSE", "scope", "ALL")),
                misc("t.base", custom("ROTATE_HP", "direction", "CLOCKWISE")),
                misc("t.hug", custom("SHARE_ACCUMULATED", "unit", 5)),
                misc("t.blackhole", custom("BANISH_CURSES")),
                tagged("t.water", CardCategory.ATTACK, 10, Set.of(), false,
                        effect("FIELD_LOCK", "filter", Map.of("category", "ATTACK"), "turns", 2)));
    }

    private static TestGame game(String... players) {
        return TestGame.with(testPack(), players);
    }

    @Test
    @DisplayName("연설: 손패를 강한 카드로 바꾸고, 다른 사람이 가진 카드는 다음 후보로 넘어간다")
    void speech() {
        TestGame g = game("p1", "p2");
        g.p("p1").setHandLimit(7);
        g.give("p2", "t.snipe");
        g.play("p1", g.give("p1", "t.speech"));
        List<String> hand = g.p("p1").getHand().stream().map(CardInstance::cardId).toList();
        assertEquals(5, g.p("p1").getHandLimit());
        assertEquals(5, hand.size(), "연설 4칸 + 정산 드로우로 한도 5까지");
        assertEquals("t.storm", hand.get(0));
        assertEquals("t.punch", hand.get(1));
        assertEquals("t.noway", hand.get(2), "저격은 p2가 가지고 있어 어림없는 소리");
    }

    @Test
    @DisplayName("야구빠따: 락 때문에 막혔을 상황에서 내면 락을 건 사람에게 45 피해")
    void baseballBatHitsLockOwner() {
        TestGame g = game("p1", "p2", "p3");
        g.state.setField(List.of(new FieldCard("f1", "t.water", "p3", 10, 0)));
        g.state.getFieldLocks().add(new FieldLock("f1", "t.water", "p3", Map.of("category", "ATTACK"), 99));
        g.p("p1").setCurse(new CurseState("curse.lego", "t.lego", "p2",
                Map.of("locks", List.of(Map.of("category", "ATTACK")))));
        g.play("p1", g.give("p1", "t.bat"));
        assertEquals(155, g.p("p3").getHp(), "필드 락을 건 사람");
        assertEquals(155, g.p("p2").getHp(), "저주를 건 사람");

        TestGame free = game("p1", "p2");
        free.play("p1", free.give("p1", "t.bat"));
        assertEquals(200, free.p("p2").getHp(), "막힌 상황이 아니면 평범한 공격 카드");
    }

    @Test
    @DisplayName("유성우: 필드·필드 락·손패·저주를 모두 없애고 새로 받는다")
    void meteorShower() {
        TestGame g = game("p1", "p2");
        g.state.setField(List.of(new FieldCard("f1", "t.water", "p2", 10, 0)));
        g.state.getFieldLocks().add(new FieldLock("f1", "t.water", "p2", Map.of("category", "ATTACK"), 99));
        g.p("p2").setCurse(new CurseState("curse.lego", "t.lego", "p1", Map.of()));
        g.play("p1", g.give("p1", "t.meteor"));
        assertTrue(g.state.getFieldLocks().isEmpty());
        assertEquals(List.of("t.meteor"), g.state.getField().stream().map(FieldCard::cardId).toList());
        assertNull(g.p("p2").getCurse());
        assertEquals(5, g.p("p2").getHand().size());
    }

    @Test
    @DisplayName("본진이 바뀐다: 모두 옆 사람의 체력을 받는다")
    void rotateHp() {
        TestGame g = game("p1", "p2", "p3").hp("p1", 10).hp("p2", 20).hp("p3", 30);
        g.play("p1", g.give("p1", "t.base"));
        assertEquals(20, g.p("p1").getHp());
        assertEquals(30, g.p("p2").getHp());
        assertEquals(10, g.p("p3").getHp());
    }

    @Test
    @DisplayName("안아줘요: 누적 100을 4명이 나누면 (5×4=20)마다 5씩 → 각자 25, 체인 종료")
    void shareAccumulated() {
        TestGame g = game("p1", "p2", "p3", "p4").chain(30, 100);
        g.play("p1", g.give("p1", "t.hug"));
        for (String p : List.of("p1", "p2", "p3", "p4")) {
            assertEquals(175, g.p(p).getHp(), p);
        }
        assertEquals(0, g.state.getAccumulatedDamage());
    }

    @Test
    @DisplayName("블랙홀: 걸린 저주를 풀고, 그 저주 카드와 블랙홀은 더 이상 뽑히지 않는다")
    void blackHole() {
        TestGame g = game("p1", "p2");
        g.p("p2").setCurse(new CurseState("curse.lego", "t.lego", "p1", Map.of()));
        g.play("p1", g.give("p1", "t.blackhole"));
        assertNull(g.p("p2").getCurse());
        assertTrue(g.state.getBannedCardIds().containsAll(List.of("t.lego", "t.blackhole")));
        for (int i = 0; i < 300; i++) {
            String drawn = Draws.one(g.state, g.pack).id();
            assertTrue(!drawn.equals("t.lego") && !drawn.equals("t.blackhole"), drawn);
        }

        TestGame none = game("p1", "p2");
        none.play("p1", none.give("p1", "t.blackhole"));
        assertTrue(none.state.getBannedCardIds().isEmpty(), "제외할 저주가 없으면 블랙홀도 남는다");
    }

    @Test
    @DisplayName("팩 검증: 모르는 커스텀 동작과 동작별 파라미터를 검사한다")
    void validation() {
        PackValidator validator = new PackValidator(EffectRegistry.defaults());
        assertTrue(validator.validate(testPack()).isEmpty(), validator.validate(testPack()).toString());
        List<String> errors = validator.validate(pack(
                misc("t.bad1", custom("NOPE")),
                misc("t.bad2", custom("ROTATE_HP", "unit", 5)),
                misc("t.bad3", custom("SPEECH", "picks", List.of(List.of("t.nope"))))));
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.bad1.effects[0].handler")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.bad2.effects[0].unit")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.bad3.effects[0].picks[0]")), errors.toString());
    }
}
