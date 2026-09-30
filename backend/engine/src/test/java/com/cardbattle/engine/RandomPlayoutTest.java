package com.cardbattle.engine;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.command.DiscardCommand;
import com.cardbattle.engine.command.PlayCommand;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.event.GameEvent;
import com.cardbattle.engine.result.ActionResult;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;
import com.cardbattle.engine.view.CardPlayabilityView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 무작위로 수백 판을 끝까지 돌려서 엔진이 예외 없이 동작하고
 * 불변 조건이 깨지지 않는지 확인한다. 효과 조합 버그를 잡는 안전망.
 */
class RandomPlayoutTest {

    private static final int GAMES = 300;
    private static final int MAX_ACTIONS = 3000;

    @Test
    @DisplayName("무작위 300판: 예외 없음, 불변 조건 유지, 대부분 정상 종료 (자리 비움·강퇴 포함)")
    void randomGamesKeepInvariants() {
        CardPack pack = TestCards.standardPack();
        GameEngine engine = new GameEngine(pack, EffectRegistry.defaults(), TestGame.CLOCK, 0);
        Random random = new Random(20260929L);
        int finished = 0;
        int kicks = 0;

        for (int game = 0; game < GAMES; game++) {
            int playerCount = 2 + random.nextInt(5);
            List<PlayerSeed> seeds = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                seeds.add(new PlayerSeed("p" + i, "플레이어" + i));
            }
            GameState state = engine.start("g" + game, GameSettings.defaults(), seeds, random.nextLong()).state();
            long lastSeq = state.getNextSeq() - 1;
            // 세 판에 한 판은 한 명이 계속 시간 초과한다 (자리 비움 → 강퇴 경로 검증)
            String afk = random.nextInt(3) == 0 ? seeds.get(random.nextInt(playerCount)).playerId() : null;

            for (int action = 0; action < MAX_ACTIONS && state.inProgress(); action++) {
                assertInvariants(state, pack);
                long versionBefore = state.getVersion();
                PlayerState away = state.alivePlayers().stream().filter(PlayerState::away).findFirst().orElse(null);
                ActionResult result = away != null && random.nextInt(4) == 0
                        ? engine.kick(state, away.getPlayerId())
                        : state.currentPlayer().getPlayerId().equals(afk)
                        ? engine.timeout(state)
                        : randomAction(engine, pack, state, random);
                if (away != null && result.accepted() && !away.alive()) {
                    kicks++;
                }
                if (!result.accepted()) {
                    fail("무작위 행동이 거절됨: " + result.rejection());
                }
                assertEquals(versionBefore + 1, state.getVersion());
                for (GameEvent e : result.events()) {
                    assertEquals(lastSeq + 1, e.seq());
                    lastSeq = e.seq();
                }
            }
            if (!state.inProgress()) {
                finished++;
                assertTrue(state.alivePlayers().size() <= 1);
            }
        }
        assertTrue(finished >= GAMES * 0.95, "정상 종료 " + finished + " / " + GAMES);
        assertTrue(kicks > 0, "강퇴가 한 번도 일어나지 않음");
    }

    private static ActionResult randomAction(GameEngine engine, CardPack pack, GameState state, Random random) {
        PlayerState me = state.currentPlayer();
        int roll = random.nextInt(100);
        if (roll < 3) {
            return engine.timeout(state);
        }
        List<CardPlayabilityView> playable = engine.snapshot(state, me.getPlayerId()).playability().stream()
                .filter(CardPlayabilityView::playable).toList();
        if (roll < 15 || playable.isEmpty()) {
            CardInstance card = me.getHand().get(random.nextInt(me.getHand().size()));
            return engine.discard(state, new DiscardCommand(me.getPlayerId(), card.instanceId(), state.getVersion()));
        }
        CardPlayabilityView pick = playable.get(random.nextInt(playable.size()));
        CardDefinition def = pack.card(me.findInHand(pick.instanceId()).cardId());
        String target = null;
        if (def.targeting().requiresChoice()) {
            List<PlayerState> candidates = state.alivePlayers().stream()
                    .filter(p -> def.targeting().allowsSelf() || p != me).toList();
            target = candidates.get(random.nextInt(candidates.size())).getPlayerId();
        }
        return engine.play(state, new PlayCommand(me.getPlayerId(), pick.instanceId(), target, state.getVersion()));
    }

    private static void assertInvariants(GameState state, CardPack pack) {
        assertTrue(state.getCurrentAttack() >= 0, "A < 0");
        assertTrue(state.getAccumulatedDamage() >= 0, "D < 0");
        assertTrue(state.currentPlayer().alive(), "현재 차례가 탈락자");
        assertTrue(state.alivePlayers().size() >= 2, "진행 중인데 생존자 2명 미만");
        for (PlayerState p : state.getPlayers()) {
            if (p.alive()) {
                assertTrue(p.getHp() > 0 && p.getHp() <= p.getHpCap(), "체력 범위 벗어남: " + p.getHp());
                assertEquals(p.getHandLimit(), p.getHand().size());
                for (CardInstance c : p.getHand()) {
                    assertTrue(pack.card(c.cardId()) != null, "팩에 없는 카드");
                }
            } else {
                assertTrue(p.getHand().isEmpty(), "탈락자 손패가 남아 있음");
            }
        }
    }
}
