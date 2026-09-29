package com.cardbattle.engine;

import com.cardbattle.engine.result.ActionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 7.4 누적 판정 표를 한 줄씩 검증한다.
 * A = 현재 공격력, D = 누적 데미지, 모든 플레이어의 시작 체력은 200.
 */
class AccumulationRulesTest {

    @Test
    @DisplayName("빈 체인에 공격 카드를 내면 A = p, D = p 로 체인이 시작된다")
    void attackOnEmptyChainStartsChain() {
        TestGame g = TestGame.of("p1", "p2", "p3");
        String card = g.give("p1", "t.a10");

        ActionResult r = g.play("p1", card);

        assertTrue(r.accepted());
        assertEquals(10, g.state.getCurrentAttack());
        assertEquals(10, g.state.getAccumulatedDamage());
        assertEquals(200, g.p("p1").getHp());
        assertEquals("p2", g.current());
    }

    @Test
    @DisplayName("현재 공격력보다 높은 카드는 누적을 쌓고 넘긴다")
    void higherAttackAddsToChain() {
        TestGame g = TestGame.of("p1", "p2", "p3").chain(10, 30);
        g.play("p1", g.give("p1", "t.a20"));

        assertEquals(20, g.state.getCurrentAttack());
        assertEquals(50, g.state.getAccumulatedDamage());
        assertEquals(200, g.p("p1").getHp());
    }

    @Test
    @DisplayName("D7: 현재 공격력과 같은 카드도 누적을 쌓고 넘긴다")
    void equalAttackContinuesChain() {
        TestGame g = TestGame.of("p1", "p2", "p3").chain(20, 30);
        g.play("p1", g.give("p1", "t.a20"));

        assertEquals(20, g.state.getCurrentAttack());
        assertEquals(50, g.state.getAccumulatedDamage());
        assertEquals(200, g.p("p1").getHp());
        assertEquals("p2", g.current());
    }

    @Test
    @DisplayName("D8: 낮은 카드를 내면 누적을 전부 받고, 그 카드로 새 체인이 시작된다")
    void lowerAttackReceivesAndRestarts() {
        TestGame g = TestGame.of("p1", "p2", "p3").chain(30, 60);
        g.play("p1", g.give("p1", "t.a10"));

        assertEquals(140, g.p("p1").getHp());
        assertEquals(10, g.state.getCurrentAttack());
        assertEquals(10, g.state.getAccumulatedDamage());
        assertEquals("p2", g.current());
    }

    @Test
    @DisplayName("데미지 감소 카드는 줄이고 남은 누적을 받은 뒤 체인을 끝낸다")
    void reduceCardReceivesRemainderAndEndsChain() {
        TestGame g = TestGame.of("p1", "p2", "p3").chain(30, 60);
        g.play("p1", g.give("p1", "t.reduce30"));

        assertEquals(170, g.p("p1").getHp());
        assertEquals(0, g.state.getCurrentAttack());
        assertEquals(0, g.state.getAccumulatedDamage());
    }

    @Test
    @DisplayName("누적 초기화 카드는 피해 없이 체인을 끝낸다")
    void resetCardEndsChainWithoutDamage() {
        TestGame g = TestGame.of("p1", "p2", "p3").chain(30, 60);
        g.play("p1", g.give("p1", "t.reset"));

        assertEquals(200, g.p("p1").getHp());
        assertEquals(0, g.state.getCurrentAttack());
        assertEquals(0, g.state.getAccumulatedDamage());
    }

    @Test
    @DisplayName("다음 차례로 넘기는 카드는 받지 않고 A·D를 유지한 채 넘긴다")
    void passMovesChainToNextPlayer() {
        TestGame g = TestGame.of("p1", "p2", "p3").chain(30, 60);
        g.play("p1", g.give("p1", "t.pass"));

        assertEquals(200, g.p("p1").getHp());
        assertEquals(30, g.state.getCurrentAttack());
        assertEquals(60, g.state.getAccumulatedDamage());
        assertEquals("p2", g.current());
    }

    @Test
    @DisplayName("배수 전달은 누적 데미지를 배수로 늘려 넘긴다")
    void doublePassMultipliesAccumulated() {
        TestGame g = TestGame.of("p1", "p2", "p3").chain(30, 60);
        g.play("p1", g.give("p1", "t.pass2x"));

        assertEquals(30, g.state.getCurrentAttack());
        assertEquals(120, g.state.getAccumulatedDamage());
    }

    @Test
    @DisplayName("즉시 전달(이전 사람)은 이전 차례 플레이어가 바로 받고 체인이 끝난다")
    void reflectHitsPreviousPlayerImmediately() {
        TestGame g = TestGame.of("p1", "p2", "p3").turn("p2").chain(30, 60);
        g.play("p2", g.give("p2", "t.reflect"));

        assertEquals(140, g.p("p1").getHp());
        assertEquals(200, g.p("p2").getHp());
        assertEquals(0, g.state.getAccumulatedDamage());
        assertEquals(0, g.state.getCurrentAttack());
        assertEquals("p3", g.current());
    }

    @Test
    @DisplayName("즉시 전달(지정 대상)은 고른 사람이 바로 받는다")
    void snipeHitsChosenTarget() {
        TestGame g = TestGame.of("p1", "p2", "p3").chain(30, 60);
        g.play("p1", g.give("p1", "t.snipe"), "p3");

        assertEquals(200, g.p("p1").getHp());
        assertEquals(200, g.p("p2").getHp());
        assertEquals(140, g.p("p3").getHp());
        assertEquals(0, g.state.getAccumulatedDamage());
    }

    @Test
    @DisplayName("누적이 있을 때 이득 카드를 내면 효과 적용 후 누적을 받는다")
    void benefitWithAccumulatedReceives() {
        TestGame g = TestGame.of("p1", "p2").hp("p1", 100).chain(30, 60);
        g.play("p1", g.give("p1", "t.heal20"));

        assertEquals(60, g.p("p1").getHp()); // 100 + 20 - 60
        assertEquals(0, g.state.getAccumulatedDamage());
        assertEquals(0, g.state.getCurrentAttack());
    }

    @Test
    @DisplayName("누적이 없을 때 이득 카드는 효과만 적용된다")
    void benefitWithoutAccumulatedOnlyApplies() {
        TestGame g = TestGame.of("p1", "p2").hp("p1", 100);
        g.play("p1", g.give("p1", "t.heal20"));

        assertEquals(120, g.p("p1").getHp());
    }

    @Test
    @DisplayName("누적이 있을 때 버리면 누적을 받고, 필드는 바뀌지 않는다")
    void discardWithAccumulatedReceivesAndKeepsField() {
        TestGame g = TestGame.of("p1", "p2", "p3").chain(30, 60).field("t.a30");
        g.discard("p1", g.give("p1", "t.a5"));

        assertEquals(140, g.p("p1").getHp());
        assertEquals(0, g.state.getAccumulatedDamage());
        assertEquals("t.a30", g.state.getField().get(0).cardId());
    }

    @Test
    @DisplayName("누적이 없을 때 버리면 아무 일도 없다")
    void discardWithoutAccumulatedDoesNothing() {
        TestGame g = TestGame.of("p1", "p2");
        g.discard("p1", g.give("p1", "t.a5"));

        assertEquals(200, g.p("p1").getHp());
        assertEquals(0, g.state.getAccumulatedDamage());
        assertEquals("p2", g.current());
    }

    @Test
    @DisplayName("ON_RECEIVE: 받은 뒤 시작된 새 체인에 효과가 적용된다 (15 + 30)")
    void onReceiveEffectAppliesToNewChain() {
        TestGame g = TestGame.of("p1", "p2", "p3").chain(30, 60);
        g.play("p1", g.give("p1", "t.drag"));

        assertEquals(140, g.p("p1").getHp());
        assertEquals(15, g.state.getCurrentAttack());
        assertEquals(45, g.state.getAccumulatedDamage());
    }

    @Test
    @DisplayName("ON_RECEIVE: 받은 게 없으면 발동하지 않는다")
    void onReceiveNotTriggeredWithoutReceiving() {
        TestGame g = TestGame.of("p1", "p2", "p3");
        g.play("p1", g.give("p1", "t.drag"));

        assertEquals(15, g.state.getCurrentAttack());
        assertEquals(15, g.state.getAccumulatedDamage());
    }

    @Test
    @DisplayName("제출한 카드가 필드를 교체한다")
    void playReplacesField() {
        TestGame g = TestGame.of("p1", "p2").field("t.a30");
        String card = g.give("p1", "t.a10");
        g.play("p1", card);

        assertEquals(1, g.state.getField().size());
        assertEquals(card, g.state.getField().get(0).instanceId());
        assertEquals("p1", g.state.getField().get(0).ownerId());
    }

    @Test
    @DisplayName("반동 데미지는 쓴 사람의 체력을 깎는다")
    void recoilDamagesSelf() {
        TestGame g = TestGame.of("p1", "p2");
        g.play("p1", g.give("p1", "t.recoil"));

        assertEquals(190, g.p("p1").getHp());
        assertEquals(40, g.state.getAccumulatedDamage());
    }

    @Test
    @DisplayName("랜덤 공격력은 범위 안에서 정해지고, 필드와 체인에 같은 값이 쓰인다")
    void randomAttackStaysInRange() {
        for (long seed = 1; seed <= 50; seed++) {
            TestGame g = TestGame.of("p1", "p2");
            g.state.setRngState(seed);
            g.play("p1", g.give("p1", "t.dice"));

            int a = g.state.getCurrentAttack();
            assertTrue(a >= 5 && a <= 30, "attack out of range: " + a);
            assertEquals(a, g.state.getAccumulatedDamage());
            assertEquals(a, g.state.getField().get(0).attack());
        }
    }
}
