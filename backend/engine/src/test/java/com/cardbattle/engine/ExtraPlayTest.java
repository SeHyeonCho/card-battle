package com.cardbattle.engine;

import com.cardbattle.engine.card.CardCategory;
import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.card.Targeting;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.pack.PackValidator;
import com.cardbattle.engine.result.ActionResult;
import com.cardbattle.engine.result.PlayBlockReason;
import com.cardbattle.engine.state.FieldCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.cardbattle.engine.TestCards.attack;
import static com.cardbattle.engine.TestCards.card;
import static com.cardbattle.engine.TestCards.effect;
import static com.cardbattle.engine.TestCards.pack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 2 추가 제출 (PRD 7.2 EXTRA_PLAY): 폭풍, 슈퍼파워, 시간아 멈춰라!, 컨슘, 전군 돌격! */
class ExtraPlayTest {

    private static CardDefinition benefit(String id, EffectSpec... e) {
        return card(id, CardCategory.BENEFIT, Targeting.NONE, List.of(), e);
    }

    private static final Map<String, Object> ATTACK_ONLY = Map.of("category", "ATTACK");

    private static CardPack testPack() {
        return pack(
                attack("t.a10", 10),
                attack("t.a30", 30),
                attack("t.punch", 100),
                attack("t.recoil", 40, effect("DAMAGE", "target", "SELF", "amount", 10)),
                attack("t.water", 10, effect("FIELD_LOCK", "filter", ATTACK_ONLY, "turns", 2)),
                new CardDefinition("t.dice", "t.dice", CardCategory.ATTACK, null, null, 5, 30, Set.of(),
                        Targeting.NONE, false, List.of(), List.of(), "5~30", null, 5),
                attack("t.storm", 20, effect("EXTRA_PLAY", "filter", ATTACK_ONLY, "count", 1,
                        "attackMode", "SUM", "discardIfNone", true)),
                benefit("t.super", effect("EXTRA_PLAY", "count", 1, "attackMode", "DOUBLE",
                        "noDoubleCardIds", List.of("t.punch"))),
                benefit("t.stop", effect("IMMUNE_THIS_TURN"), effect("EXTRA_PLAY", "count", 1),
                        effect("LOCK_NEXT_PLAYER")),
                benefit("t.consume", effect("EXTRA_PLAY", "filter", ATTACK_ONLY, "count", 1,
                        "attackMode", "HEAL_SELF", "discardIfNone", true)),
                benefit("t.charge", effect("PLAY_ALL", "filter", ATTACK_ONLY, "suppressOnPlayEffects", true,
                        "randomAttackAs", 15)),
                benefit("t.heal", effect("HEAL", "target", "SELF", "amount", 20)));
    }

    private static TestGame game(String... players) {
        return TestGame.with(testPack(), players);
    }

    @Test
    @DisplayName("폭풍: 턴이 끝나지 않고 공격 카드 한 장을 더 내면 공격력이 합쳐진다")
    void storm() {
        TestGame g = game("p1", "p2").chain(10, 40);
        String a30 = g.give("p1", "t.a30");
        g.give("p1", "t.heal");
        g.play("p1", g.give("p1", "t.storm"));
        assertEquals("p1", g.current(), "아직 p1의 턴");
        assertNotNull(g.state.getExtraPlay());
        assertEquals(1, g.events(EventType.EXTRA_PLAY_STARTED).size());
        assertEquals(40, g.state.getAccumulatedDamage(), "판정은 아직");

        assertEquals(PlayBlockReason.EXTRA_PLAY,
                g.play("p1", g.p("p1").getHand().stream().filter(c -> c.cardId().equals("t.heal"))
                        .findFirst().orElseThrow().instanceId()).rejection().reason(),
                "공격 카드만 낼 수 있다");

        g.play("p1", a30);
        assertNull(g.state.getExtraPlay());
        assertEquals(50, g.state.getCurrentAttack());
        assertEquals(90, g.state.getAccumulatedDamage());
        assertEquals("p2", g.current());
        assertEquals(List.of("t.storm", "t.a30"), g.state.getField().stream().map(FieldCard::cardId).toList());
    }

    @Test
    @DisplayName("폭풍: 공격 카드가 없으면 한 장을 버려야 하고, 폭풍만으로 판정한다")
    void stormWithoutAttackMustDiscard() {
        TestGame g = game("p1", "p2").chain(30, 60);
        String heal = g.give("p1", "t.heal");
        g.play("p1", g.give("p1", "t.storm"));
        assertTrue(g.state.getExtraPlay().isDiscardOnly());
        assertEquals(PlayBlockReason.EXTRA_PLAY, g.play("p1", heal).rejection().reason());

        g.discard("p1", heal);
        assertEquals("p2", g.current());
        assertEquals(20, g.state.getCurrentAttack(), "20 < 30 이라 누적을 받고 폭풍으로 새 체인");
        assertEquals(140, g.p("p1").getHp());
    }

    @Test
    @DisplayName("슈퍼파워: 다음 공격 카드의 공격력이 2배, 원펀치는 제외")
    void superPower() {
        TestGame g = game("p1", "p2");
        String a30 = g.give("p1", "t.a30");
        g.play("p1", g.give("p1", "t.super"));
        g.play("p1", a30);
        assertEquals(60, g.state.getCurrentAttack());

        TestGame p = game("p1", "p2");
        String punch = p.give("p1", "t.punch");
        p.play("p1", p.give("p1", "t.super"));
        p.play("p1", punch);
        assertEquals(100, p.state.getCurrentAttack());
    }

    @Test
    @DisplayName("시간아 멈춰라! → 폭풍 → 원펀치: 나는 받지 않고, 120이 쌓인 채 다음 사람은 행동 불가로 그대로 맞는다")
    void timeStopCombo() {
        TestGame g = game("p1", "p2", "p3").chain(40, 80);
        String storm = g.give("p1", "t.storm");
        String punch = g.give("p1", "t.punch");
        g.play("p1", g.give("p1", "t.stop"));
        g.play("p1", storm);
        g.play("p1", punch);
        assertEquals(200, g.p("p1").getHp());
        assertEquals(0, g.state.getAccumulatedDamage(), "p2가 받아서 체인이 끝났다");
        assertEquals(0, g.p("p2").getHp(), "80 + 120 = 200");
        assertTrue(g.p("p2").isEliminated());
        assertEquals("p3", g.current());
    }

    @Test
    @DisplayName("컨슘: 낸 공격 카드의 공격력만큼 회복하고, 누적은 그대로 받는다")
    void consume() {
        TestGame g = game("p1", "p2").chain(10, 50).hp("p1", 100);
        String recoil = g.give("p1", "t.a30");
        g.play("p1", g.give("p1", "t.consume"));
        g.play("p1", recoil);
        assertEquals(80, g.p("p1").getHp(), "100 + 30 - 50");
        assertEquals(0, g.state.getAccumulatedDamage());
    }

    @Test
    @DisplayName("추가 제출 중 버리기: 그때까지 낸 카드로 판정한다")
    void discardDuringCombo() {
        TestGame g = game("p1", "p2");
        g.give("p1", "t.a30");
        String other = g.give("p1", "t.heal");
        g.play("p1", g.give("p1", "t.storm"));
        g.discard("p1", other);
        assertEquals(20, g.state.getCurrentAttack());
        assertEquals("t.storm", g.state.getField().get(0).cardId());
    }

    @Test
    @DisplayName("추가 제출 중 시간 초과: 무작위로 버리고 그때까지 낸 카드로 판정한다")
    void timeoutDuringCombo() {
        TestGame g = game("p1", "p2");
        g.give("p1", "t.a30");
        g.play("p1", g.give("p1", "t.storm"));
        g.timeout();
        assertNull(g.state.getExtraPlay());
        assertEquals(20, g.state.getCurrentAttack());
        assertEquals("p2", g.current());
    }

    @Test
    @DisplayName("전군 돌격!: 공격 카드를 전부 내고 합산, 제출 효과는 무시하되 필드 락은 남고 주사위는 15")
    void chargeAll() {
        TestGame g = game("p1", "p2");
        List<String> attacks = List.of(g.give("p1", "t.a10"), g.give("p1", "t.recoil"), g.give("p1", "t.water"),
                g.give("p1", "t.dice"));
        String heal = g.give("p1", "t.heal");
        g.play("p1", g.give("p1", "t.charge"));
        assertEquals(75, g.state.getCurrentAttack(), "10 + 40 + 10 + 15");
        assertEquals(200, g.p("p1").getHp(), "반동 효과는 발동하지 않는다");
        assertEquals(5, g.state.getField().size());
        assertEquals(1, g.state.getFieldLocks().size(), "물총의 필드 락은 남는다");
        assertTrue(g.p("p1").getHand().stream().noneMatch(c -> attacks.contains(c.instanceId())));
        assertTrue(g.p("p1").getHand().stream().anyMatch(c -> c.instanceId().equals(heal)), "공격 카드가 아니면 남는다");
    }

    @Test
    @DisplayName("스냅샷에 추가 제출 상태가 들어간다 (새로고침 복구)")
    void snapshotShowsExtraPlay() {
        TestGame g = game("p1", "p2");
        g.give("p1", "t.a30");
        g.play("p1", g.give("p1", "t.storm"));
        assertNotNull(g.engine.snapshot(g.state, "p1").extraPlay());
        assertEquals("SUM", g.engine.snapshot(g.state, "p1").extraPlay().getMode());
    }

    @Test
    @DisplayName("팩 검증")
    void validation() {
        PackValidator validator = new PackValidator(EffectRegistry.defaults());
        assertTrue(validator.validate(testPack()).isEmpty(), validator.validate(testPack()).toString());
        List<String> errors = validator.validate(pack(
                benefit("t.bad", effect("EXTRA_PLAY", "count", 2, "attackMode", "TRIPLE"))));
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.bad.effects[0].count")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.bad.effects[0].attackMode")), errors.toString());
    }

    @Test
    @DisplayName("추가 제출이 끝나야 차례가 넘어간다: 한 턴 행동으로 여러 요청을 보내도 버전은 요청마다 오른다")
    void comboIsMultipleActions() {
        TestGame g = game("p1", "p2");
        String a30 = g.give("p1", "t.a30");
        ActionResult first = g.play("p1", g.give("p1", "t.storm"));
        ActionResult second = g.play("p1", a30);
        assertTrue(first.accepted() && second.accepted());
        assertTrue(second.events().stream().anyMatch(e -> e.type() == EventType.TURN_ENDED));
        assertTrue(first.events().stream().noneMatch(e -> e.type() == EventType.TURN_ENDED));
    }
}
