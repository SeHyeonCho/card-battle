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
import com.cardbattle.engine.result.RejectCode;
import com.cardbattle.engine.rules.Statuses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.cardbattle.engine.TestCards.attack;
import static com.cardbattle.engine.TestCards.card;
import static com.cardbattle.engine.TestCards.effect;
import static com.cardbattle.engine.TestCards.pack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 2 저주(PRD 7.8)와 지속 상태: 걸기·발동·해제·반사·락·지속 효과 */
class CurseAndStatusTest {

    private static CardDefinition curse(String id, Map<String, Object> def) {
        return card(id, CardCategory.CURSE, Targeting.CHOSEN_ANY, List.of(),
                effect("APPLY_CURSE", "target", "CHOSEN", "curse", def));
    }

    private static CardDefinition benefit(String id, Targeting targeting, EffectSpec... e) {
        return card(id, CardCategory.BENEFIT, targeting, List.of(), e);
    }

    private static Map<String, Object> dmg(String target, int amount) {
        return Map.of("type", "DAMAGE", "target", target, "amount", amount);
    }

    private static CardPack testPack() {
        return pack(
                attack("t.a10", 10),
                attack("t.a40", 40),
                attack("t.fx", 20, effect("HEAL", "target", "SELF", "amount", 5)),
                card("t.pass", CardCategory.SUPPORT, Targeting.NONE, List.of(),
                        effect("TRANSFER_ACCUMULATED", "to", "NEXT")),
                curse("t.lego", Map.of("id", "curse.lego", "onTurnEnd", List.of(dmg("CURSED", 10)))),
                curse("t.kiss", Map.of("id", "curse.kiss", "onTurnEnd", List.of(dmg("CURSED", 10),
                        Map.of("type", "HEAL", "target", "CASTER", "amount", 10)))),
                curse("t.eye", Map.of("id", "curse.eye", "locks", List.of(Map.of("category", "SUPPORT")))),
                curse("t.crisis", Map.of("id", "curse.crisis", "passives", List.of(
                        Map.of("type", "HP_CAP", "value", 100),
                        Map.of("type", "HEAL_MULTIPLIER", "value", 0.5, "exceptCategories", List.of("MISC"))))),
                curse("t.shock", Map.of("id", "curse.shock", "passives", List.of(
                        Map.of("type", "EXTRA_RECEIVE_DAMAGE", "value", 20)))),
                curse("t.friends", Map.of("id", "curse.friends", "passives", List.of(
                        Map.of("type", "REDIRECT_CASTER_RECEIVE", "onlyWhenAttackCard", true)))),
                curse("t.small", Map.of("id", "curse.small", "passives", List.of(
                        Map.of("type", "HAND_LIMIT_FIXED", "value", 4)))),
                curse("t.cosmo", Map.of("id", "curse.cosmo", "passives", List.of(
                        Map.of("type", "PLAY_BECOMES_DISCARD", "p", 1.0, "exceptAlwaysPlayable", true)))),
                curse("t.stop", Map.of("id", "curse.stop", "passives", List.of(Map.of("type", "FORCE_SELF_TARGET")))),
                curse("t.count", Map.of("id", "curse.count", "passives", List.of(
                        Map.of("type", "TURN_TIME", "seconds", 3)))),
                curse("t.proper", Map.of("id", "curse.proper", "passives", List.of(
                        Map.of("type", "STRIP_ATTACK_EFFECTS")))),
                curse("t.watch", Map.of("id", "curse.watch", "passives", List.of(Map.of("type", "REVEAL_HAND")))),
                benefit("t.archangel", Targeting.NONE, effect("REMOVE_CURSE", "scope", "SELF")),
                benefit("t.pope", Targeting.NONE, effect("REMOVE_CURSE", "scope", "ALL")),
                benefit("t.mine", Targeting.NONE, effect("REFLECT_CURSE", "scope", "SELF")),
                benefit("t.heal40", Targeting.NONE, effect("HEAL", "target", "SELF", "amount", 40)),
                benefit("t.shoot", Targeting.CHOSEN_OTHER, effect("DAMAGE", "target", "CHOSEN", "amount", 30)),
                benefit("t.shield", Targeting.NONE,
                        effect("APPLY_STATUS", "target", "SELF", "status", "UNTARGETABLE", "turns", 3)),
                benefit("t.apple", Targeting.NONE, effect("APPLY_STATUS", "target", "SELF", "status", "REGEN",
                        "turns", 4, "params", Map.of("amount", 15))),
                attack("t.stim", 10, effect("APPLY_STATUS", "target", "SELF", "status", "NEXT_ATTACK_BONUS",
                        "turns", 1, "params", Map.of("value", 30))),
                benefit("t.emp", Targeting.NONE, effect("DISPEL_STATUSES", "target", "ALL_OTHERS",
                        "filter", Map.of("statuses", List.of("UNTARGETABLE", "REGEN")))));
    }

    private static TestGame game(String... players) {
        return TestGame.with(testPack(), players);
    }

    /** p1이 target에게 저주 카드를 낸다. 저주는 대상의 턴이 끝날 때 발동하므로 여기서는 걸리기만 한다 */
    private static void curse(TestGame g, String cardId, String target) {
        g.turn("p1");
        assertTrue(g.play("p1", g.give("p1", cardId), target).accepted());
    }

    @Test
    @DisplayName("저주는 대상에게 걸리고, 대상의 턴이 끝날 때 발동한다 (레고뿌리기)")
    void curseTriggersOnCursedTurnEnd() {
        TestGame g = game("p1", "p2");
        curse(g, "t.lego", "p2");
        assertEquals(200, g.p("p2").getHp(), "건 사람의 턴 종료에는 발동하지 않는다");
        assertNotNull(g.p("p2").getCurse());
        assertEquals("p1", g.p("p2").getCurse().getCasterId());

        g.play("p2", g.give("p2", "t.a10"));
        assertEquals(190, g.p("p2").getHp());
    }

    @Test
    @DisplayName("CASTER: 저주를 건 사람이 회복한다 (딥키스), 자기에게 걸면 서로 상쇄")
    void casterHeals() {
        TestGame g = game("p1", "p2").hp("p1", 100);
        curse(g, "t.kiss", "p2");
        g.play("p2", g.give("p2", "t.a10"));
        assertEquals(190, g.p("p2").getHp());
        assertEquals(110, g.p("p1").getHp());

        TestGame self = game("p1", "p2").hp("p1", 100);
        curse(self, "t.kiss", "p1");
        assertEquals(100, self.p("p1").getHp(), "자기 턴 종료에 -10 +10");
    }

    @Test
    @DisplayName("새 저주는 기존 저주를 덮어쓴다")
    void newCurseOverwrites() {
        TestGame g = game("p1", "p2");
        curse(g, "t.lego", "p2");
        curse(g, "t.eye", "p2");
        assertEquals("curse.eye", g.p("p2").getCurse().getCurseId());
    }

    @Test
    @DisplayName("저주 락: 눈뽕이 걸리면 보조 카드를 낼 수 없다")
    void curseLock() {
        TestGame g = game("p1", "p2");
        curse(g, "t.eye", "p2");
        ActionResult r = g.play("p2", g.give("p2", "t.pass"));
        assertEquals(PlayBlockReason.CURSE_LOCK, r.rejection().reason());
    }

    @Test
    @DisplayName("저주 해제: 대천사는 내 것만, 교황의 힘은 모두의 저주를 푼다")
    void removeCurse() {
        TestGame g = game("p1", "p2", "p3");
        curse(g, "t.lego", "p2");
        curse(g, "t.lego", "p3");
        g.turn("p2");
        g.play("p2", g.give("p2", "t.archangel"));
        assertNull(g.p("p2").getCurse());
        assertNotNull(g.p("p3").getCurse());

        g.turn("p1");
        g.play("p1", g.give("p1", "t.pope"));
        assertNull(g.p("p3").getCurse());
    }

    @Test
    @DisplayName("저주 반사: 이 차는 이제 제 겁니다 — 건 사람에게 그대로 돌려준다")
    void reflectCurse() {
        TestGame g = game("p1", "p2");
        curse(g, "t.lego", "p2");
        g.play("p2", g.give("p2", "t.mine"));
        assertNull(g.p("p2").getCurse());
        assertEquals("curse.lego", g.p("p1").getCurse().getCurseId());
        assertEquals("p2", g.p("p1").getCurse().getCasterId());
    }

    @Test
    @DisplayName("저주를 건 사람이 탈락하면 그 저주는 풀린다 (SC1)")
    void casterEliminationReleasesCurse() {
        TestGame g = game("p1", "p2", "p3");
        curse(g, "t.lego", "p3");
        g.hp("p1", 5).turn("p2");
        g.play("p2", g.give("p2", "t.shoot"), "p1");
        assertTrue(g.p("p1").isEliminated());
        assertNull(g.p("p3").getCurse());
    }

    @Test
    @DisplayName("위기상황 재현: 체력 상한 100, 회복 절반 (기타 카드는 제외)")
    void crisisReplay() {
        TestGame g = game("p1", "p2");
        curse(g, "t.crisis", "p2");
        g.play("p2", g.give("p2", "t.a10"));
        assertEquals(100, g.p("p2").getHp(), "정산 6단계에서 상한으로 줄어든다");

        g.hp("p2", 50).chain(0, 0).turn("p2");
        g.play("p2", g.give("p2", "t.heal40"));
        assertEquals(70, g.p("p2").getHp(), "회복 40의 절반");
    }

    @Test
    @DisplayName("충격과 공포: 누적 데미지를 받을 때 20을 더 받는다")
    void extraReceiveDamage() {
        TestGame g = game("p1", "p2");
        curse(g, "t.shock", "p2");
        g.chain(40, 60).turn("p2");
        g.play("p2", g.give("p2", "t.a10"));
        assertEquals(120, g.p("p2").getHp());
    }

    @Test
    @DisplayName("프렌즈실드: 건 사람이 공격 카드로 누적을 받으면 저주받은 사람이 대신 받는다")
    void friendsShield() {
        TestGame g = game("p1", "p2");
        curse(g, "t.friends", "p2");
        g.chain(40, 60).turn("p1");
        g.play("p1", g.give("p1", "t.a10"));
        assertEquals(200, g.p("p1").getHp());
        assertEquals(140, g.p("p2").getHp());

        TestGame noAttack = game("p1", "p2");
        curse(noAttack, "t.friends", "p2");
        noAttack.chain(40, 60).turn("p1");
        noAttack.play("p1", noAttack.give("p1", "t.heal40"));
        assertEquals(180, noAttack.p("p1").getHp(), "공격 카드가 아니면 자기가 받는다 (200 + 40 - 60)");
    }

    @Test
    @DisplayName("햄보칼수업서: 손패 한도가 4장으로 고정된다")
    void handLimitFixed() {
        TestGame g = game("p1", "p2");
        curse(g, "t.small", "p2");
        g.play("p2", g.give("p2", "t.a10"));
        assertEquals(4, g.p("p2").getHand().size());
    }

    @Test
    @DisplayName("코스모의 강화기: 카드 내기가 버리기로 바뀐다 (p=1)")
    void playBecomesDiscard() {
        TestGame g = game("p1", "p2");
        curse(g, "t.cosmo", "p2");
        g.chain(10, 30).turn("p2");
        ActionResult r = g.play("p2", g.give("p2", "t.a40"));
        assertTrue(r.accepted());
        assertEquals(1, g.events(EventType.PLAY_FUMBLED).size());
        assertEquals(170, g.p("p2").getHp(), "버리기가 되어 누적 30을 받는다");
        assertTrue(g.state.getField().isEmpty() || !"t.a40".equals(g.state.getField().get(0).cardId()),
                "버리기는 필드를 바꾸지 않는다");
    }

    @Test
    @DisplayName("멈춰!: 대상을 고르는 카드의 대상이 자기 자신이 된다")
    void forceSelfTarget() {
        TestGame g = game("p1", "p2");
        curse(g, "t.stop", "p2");
        g.play("p2", g.give("p2", "t.shoot"), "p1");
        assertEquals(200, g.p("p1").getHp());
        assertEquals(170, g.p("p2").getHp());
    }

    @Test
    @DisplayName("초읽기: 저주받은 사람의 턴 제한 시간이 3초가 된다")
    void turnTime() {
        TestGame g = game("p1", "p2");
        curse(g, "t.count", "p2");
        assertEquals("p2", g.current());
        assertEquals(TestGame.NOW.toEpochMilli() + 3000, g.state.getTurnDeadlineEpochMs());
    }

    @Test
    @DisplayName("적절한 카드: 저주받은 사람이 낸 공격 카드의 부가 효과가 무시된다")
    void stripAttackEffects() {
        TestGame g = game("p1", "p2").hp("p2", 100);
        curse(g, "t.proper", "p2");
        g.play("p2", g.give("p2", "t.fx"));
        assertEquals(100, g.p("p2").getHp(), "공격 카드의 회복 효과가 없다");
    }

    @Test
    @DisplayName("지켜보고 있다: 저주받은 사람의 손패가 모두에게 공개된다")
    void revealHand() {
        TestGame g = game("p1", "p2");
        curse(g, "t.watch", "p2");
        assertTrue(g.events(EventType.HAND_REVEALED).stream()
                .anyMatch(e -> "p2".equals(e.payload().get("playerId")) && e.recipientId() == null));
        assertEquals(g.p("p2").getHand().size(),
                g.engine.snapshot(g.state, "p1").players().get(1).revealedHand().size());
        assertNull(g.engine.snapshot(g.state, "p2").players().get(0).revealedHand());
    }

    @Test
    @DisplayName("천상의 보호막: 3턴 동안 다른 사람이 대상으로 고를 수 없다")
    void untargetable() {
        TestGame g = game("p1", "p2");
        g.turn("p2");
        g.play("p2", g.give("p2", "t.shield"));
        ActionResult r = g.play("p1", g.give("p1", "t.shoot"), "p2");
        assertEquals(RejectCode.INVALID_TARGET, r.rejection().code());

        // 보호막을 건 턴은 세지 않고, p2의 턴이 3번 더 끝나면 사라진다
        for (int i = 0; i < 3; i++) {
            assertNotNull(g.p("p2").status(Statuses.UNTARGETABLE));
            g.turn("p2");
            g.play("p2", g.give("p2", "t.a10"));
        }
        assertNull(g.p("p2").status(Statuses.UNTARGETABLE));
    }

    @Test
    @DisplayName("황금사과: 다음 4번의 내 턴 종료 때 15씩 회복")
    void regen() {
        TestGame g = game("p1", "p2").hp("p1", 100);
        g.play("p1", g.give("p1", "t.apple"));
        assertEquals(100, g.p("p1").getHp());
        for (int i = 0; i < 5; i++) {
            g.turn("p1");
            g.play("p1", g.give("p1", "t.a10"));
        }
        assertEquals(160, g.p("p1").getHp());
        assertTrue(g.p("p1").getStatuses().isEmpty());
    }

    @Test
    @DisplayName("스팀팩: 다음 차례에 내는 공격 카드 한 장의 공격력 +30")
    void stimpack() {
        TestGame g = game("p1", "p2");
        g.play("p1", g.give("p1", "t.stim"));
        assertEquals(10, g.state.getCurrentAttack(), "낸 턴의 스팀팩 자신에게는 붙지 않는다");

        g.chain(0, 0).turn("p1");
        g.play("p1", g.give("p1", "t.a10"));
        assertEquals(40, g.state.getCurrentAttack());
        assertNull(g.p("p1").status(Statuses.NEXT_ATTACK_BONUS));
    }

    @Test
    @DisplayName("이엠피 쇼크웨이브: 다른 사람의 지속 상태를 없앤다")
    void dispel() {
        TestGame g = game("p1", "p2");
        g.turn("p2");
        g.play("p2", g.give("p2", "t.shield"));
        g.turn("p1");
        g.play("p1", g.give("p1", "t.emp"));
        assertTrue(g.p("p2").getStatuses().isEmpty());
    }

    @Test
    @DisplayName("팩 검증: 저주 정의의 키·지속 효과·중첩 효과를 검사한다")
    void validation() {
        PackValidator validator = new PackValidator(EffectRegistry.defaults());
        assertTrue(validator.validate(testPack()).isEmpty(), validator.validate(testPack()).toString());

        List<String> errors = validator.validate(pack(
                curse("t.bad1", Map.of("id", "curse.bad1", "passives", List.of(Map.of("type", "NOPE")))),
                curse("t.bad2", Map.of("onTurnEnd", List.of(dmg("CURSED", 0)), "typo", 1)),
                benefit("t.bad3", Targeting.NONE,
                        effect("APPLY_STATUS", "target", "SELF", "status", "REGEN", "turns", 2))));
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.bad1.effects[0].curse.passives[0].type")),
                errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.bad2.effects[0].curse.id")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.bad2.effects[0].curse.typo")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.bad2.effects[0].curse.onTurnEnd[0].amount")),
                errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("t.bad3.effects[0].params.amount")), errors.toString());
        assertFalse(errors.isEmpty());
    }
}
