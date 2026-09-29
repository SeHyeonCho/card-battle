package com.cardbattle.server;

import com.cardbattle.engine.GameEngine;
import com.cardbattle.engine.GameSettings;
import com.cardbattle.engine.PlayerSeed;
import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.command.DiscardCommand;
import com.cardbattle.engine.command.PlayCommand;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.event.GameEvent;
import com.cardbattle.engine.pack.PackParser;
import com.cardbattle.engine.pack.PackValidator;
import com.cardbattle.engine.result.ActionResult;
import com.cardbattle.engine.rules.Passives;
import com.cardbattle.engine.rules.Statuses;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;
import com.cardbattle.engine.view.CardPlayabilityView;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 로컬 원작 카드팩(packs/original, gitignore)으로 무작위 게임을 수백 판 돌리는 안전망.
 * 폴더가 없으면(저장소를 새로 받은 경우) 건너뛴다.
 * 서버처럼 매 행동마다 GameState를 JSON으로 저장했다가 다시 읽어서 이어 간다 (Redis 저장 흉내).
 */
class OriginalPackPlayoutTest {

    private static final Path PACK_DIR = Path.of("../../packs/original");
    private static final int GAMES = 400;
    private static final int MAX_ACTIONS = 4000;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @SuppressWarnings("unchecked")
    private CardPack loadPack() throws IOException {
        Map<String, Object> meta = json.readValue(Files.readString(PACK_DIR.resolve("pack.json")), Map.class);
        List<Map<String, Object>> cards = new ArrayList<>();
        try (Stream<Path> files = Files.list(PACK_DIR.resolve("cards"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                cards.addAll(json.readValue(Files.readString(f), List.class));
            }
        }
        return PackParser.parse(meta, cards);
    }

    @Test
    @DisplayName("원작 팩: 검증 통과, 무작위 400판 동안 예외·거절·불변 조건 위반·JSON 손실 없음")
    void randomGamesWithOriginalPack() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(PACK_DIR), "packs/original 이 없어 건너뜀");
        CardPack pack = loadPack();
        List<String> errors = new PackValidator(EffectRegistry.defaults()).validate(pack);
        assertTrue(errors.isEmpty(), String.join("\n", errors));

        GameEngine engine = new GameEngine(pack, EffectRegistry.defaults(), CLOCK);
        Random random = new Random(20260929L);
        int finished = 0;
        int actions = 0;
        Map<EventType, Integer> seen = new EnumMap<>(EventType.class);
        for (int game = 0; game < GAMES; game++) {
            int playerCount = 2 + random.nextInt(5);
            List<PlayerSeed> seeds = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                seeds.add(new PlayerSeed("p" + i, "플레이어" + i));
            }
            GameState state = engine.start("g" + game, GameSettings.defaults(), seeds, random.nextLong()).state();
            long lastSeq = state.getNextSeq() - 1;
            for (int action = 0; action < MAX_ACTIONS && state.inProgress(); action++) {
                state = roundTrip(state);
                assertInvariants(state, pack, game, action);
                long versionBefore = state.getVersion();
                ActionResult result = randomAction(engine, pack, state, random);
                if (!result.accepted()) {
                    fail("게임 " + game + " 행동 " + action + " 거절: " + result.rejection());
                }
                assertEquals(versionBefore + 1, state.getVersion());
                for (GameEvent e : result.events()) {
                    assertEquals(lastSeq + 1, e.seq());
                    lastSeq = e.seq();
                    seen.merge(e.type(), 1, Integer::sum);
                }
                actions++;
            }
            if (!state.inProgress()) {
                finished++;
            }
        }
        System.out.println("원작 팩 무작위 게임: " + finished + " / " + GAMES + " 종료, 행동 " + actions + "회");
        System.out.println("이벤트: " + seen);
        // Phase 2 기능이 실제로 무작위 게임에서 일어났는지 (안전망이 헛돌지 않도록)
        for (EventType must : List.of(EventType.CURSE_APPLIED, EventType.CURSE_REMOVED, EventType.STATUS_APPLIED,
                EventType.FIELD_LOCKS_CHANGED, EventType.EXTRA_PLAY_STARTED, EventType.TURN_SKIPPED,
                EventType.TURN_LOCKED, EventType.DIRECTION_CHANGED, EventType.TIME_BOMB_EXPLODED,
                EventType.HAND_REVEALED, EventType.HAND_LIMIT_CHANGED, EventType.PLAY_FUMBLED)) {
            assertTrue(seen.getOrDefault(must, 0) > 0, must + " 가 한 번도 일어나지 않음");
        }
        assertTrue(finished >= GAMES * 0.9, "정상 종료 " + finished + " / " + GAMES);
    }

    /** Redis에 저장했다가 읽은 것처럼 JSON으로 바꿨다가 되돌린다. 두 번 바꿔도 같아야 한다 */
    private GameState roundTrip(GameState state) {
        String once = json.writeValueAsString(state);
        GameState back = json.readValue(once, GameState.class);
        assertEquals(once, json.writeValueAsString(back), "JSON 저장 후 상태가 달라짐");
        return back;
    }

    private static ActionResult randomAction(GameEngine engine, CardPack pack, GameState state, Random random) {
        PlayerState me = state.currentPlayer();
        int roll = random.nextInt(100);
        if (roll < 2) {
            return engine.timeout(state);
        }
        List<CardPlayabilityView> playable = engine.snapshot(state, me.getPlayerId()).playability().stream()
                .filter(CardPlayabilityView::playable).toList();
        if (roll < 12 || playable.isEmpty()) {
            CardInstance card = me.getHand().get(random.nextInt(me.getHand().size()));
            return engine.discard(state, new DiscardCommand(me.getPlayerId(), card.instanceId(), state.getVersion()));
        }
        CardPlayabilityView pick = playable.get(random.nextInt(playable.size()));
        CardDefinition def = pack.card(me.findInHand(pick.instanceId()).cardId());
        String target = null;
        if (def.targeting().requiresChoice()) {
            List<PlayerState> candidates = state.alivePlayers().stream()
                    .filter(p -> p == me ? def.targeting().allowsSelf() : p.status(Statuses.UNTARGETABLE) == null)
                    .toList();
            target = candidates.get(random.nextInt(candidates.size())).getPlayerId();
        }
        return engine.play(state, new PlayCommand(me.getPlayerId(), pick.instanceId(), target, state.getVersion()));
    }

    private static void assertInvariants(GameState state, CardPack pack, int game, int action) {
        String where = " (게임 " + game + ", 행동 " + action + ")";
        assertTrue(state.getCurrentAttack() >= 0, "A < 0" + where);
        assertTrue(state.getAccumulatedDamage() >= 0, "D < 0" + where);
        assertTrue(state.currentPlayer().alive(), "현재 차례가 탈락자" + where);
        assertTrue(state.alivePlayers().size() >= 2, "진행 중인데 생존자 2명 미만" + where);
        assertTrue(state.getFieldLocks().stream().allMatch(l -> l.getExpiresAfterTurn() - state.getTurnNumber() <= 2),
                "필드 락이 2턴을 넘음" + where);
        for (PlayerState p : state.getPlayers()) {
            if (p.alive()) {
                assertTrue(p.getHp() > 0 && p.getHp() <= Passives.hpCap(p), "체력 범위 벗어남: " + p.getHp() + where);
                assertTrue(!p.getHand().isEmpty(), "생존자 손패가 비어 있음" + where);
                assertTrue(p.getHand().size() <= 20, "손패가 비정상적으로 많음: " + p.getHand().size() + where);
                for (CardInstance c : p.getHand()) {
                    assertTrue(pack.card(c.cardId()) != null, "팩에 없는 카드" + where);
                }
            } else {
                assertTrue(p.getHand().isEmpty(), "탈락자 손패가 남아 있음" + where);
            }
        }
    }
}
