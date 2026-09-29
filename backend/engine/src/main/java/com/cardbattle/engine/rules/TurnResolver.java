package com.cardbattle.engine.rules;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardInstance;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.Timing;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.event.EventSink;
import com.cardbattle.engine.event.EventType;
import com.cardbattle.engine.result.Playability;
import com.cardbattle.engine.state.GameRng;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.GameStatus;
import com.cardbattle.engine.state.PlayerState;
import com.cardbattle.engine.view.CardPlayabilityView;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.cardbattle.engine.event.EventSink.payload;

/**
 * 누적 판정(PRD 7.4)과 턴 종료 정산(PRD 7.6), 턴 넘기기를 담당한다.
 */
public final class TurnResolver {

    private final CardPack pack;
    private final EffectRegistry effects;
    private final PlayabilityChecker playability;
    private final Clock clock;

    public TurnResolver(CardPack pack, EffectRegistry effects, PlayabilityChecker playability, Clock clock) {
        this.pack = pack;
        this.effects = effects;
        this.playability = playability;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // 7.4 누적 판정 — ON_PLAY 효과를 실행한 뒤 호출한다
    // ------------------------------------------------------------------

    public void judge(TurnContext ctx) {
        if (ctx.outcome() != null) {
            return; // 효과(예: 데미지 전달)가 이미 결정함
        }
        GameState state = ctx.state();
        CardDefinition card = ctx.card();
        if (card != null && card.attackCard()) {
            int a = state.getCurrentAttack();
            int p = ctx.attack();
            if (a == 0 || p >= a) {
                // 같거나 높은 공격력: 누적을 쌓고 넘긴다 (D7)
                ctx.setChain(p, state.getAccumulatedDamage() + p);
                ctx.decideOutcome(ChainOutcome.CONTINUE);
            } else {
                // 낮은 공격력: 현재 누적을 받고, 이 카드로 새 체인 시작 (D8)
                ctx.restartWith(p);
            }
        } else {
            // 공격이 아닌 카드, 버리기, 시간 초과: 남은 누적을 받고 체인 종료
            ctx.decideOutcome(ChainOutcome.RECEIVE_AND_END);
        }
    }

    // ------------------------------------------------------------------
    // 7.6 턴 종료 정산
    // ------------------------------------------------------------------

    public void settle(TurnContext ctx) {
        GameState state = ctx.state();
        PlayerState actor = ctx.actor();
        EventSink events = ctx.events();

        // 1. 누적 수령 → 체인 종료 또는 새 체인 시작
        if (ctx.outcome().receives()) {
            int d = state.getAccumulatedDamage();
            if (!ctx.immune() && d > 0) {
                ctx.damage(actor, d, "ACCUMULATED");
                ctx.markReceived();
            }
            if (ctx.outcome() == ChainOutcome.RECEIVE_AND_RESTART) {
                ctx.setChain(ctx.restartAttack(), ctx.restartAttack());
            } else {
                ctx.setChain(0, 0);
            }
        }

        // 2. 예약 효과
        if (ctx.card() != null) {
            if (ctx.received()) {
                effects.run(ctx, ctx.card().effectsAt(Timing.ON_RECEIVE));
            }
            effects.run(ctx, ctx.card().effectsAt(Timing.ON_TURN_END));
        }

        // 3. 저주 발동       — Phase 2
        // 4. 지속 상태 틱     — Phase 2
        // 5. 확률 효과(시한폭탄) — Phase 2, 항상 이 위치(맨 마지막)에서 판정

        // 6. 체력 상한 보정
        for (PlayerState p : state.getPlayers()) {
            if (p.alive() && p.getHp() > p.getHpCap()) {
                p.setHp(p.getHpCap());
            }
        }

        // 7. 탈락 판정 (정산 중 0 이하로 내려갔다가 회복했으면 생존)
        eliminate(state, events);

        // 8. 드로우
        if (actor.alive()) {
            drawUpTo(state, actor);
        }
        events.toPlayer(actor.getPlayerId(), EventType.HAND_UPDATED, payload("hand", List.copyOf(actor.getHand())));
        events.toAll(EventType.HAND_COUNT_CHANGED,
                payload("playerId", actor.getPlayerId(), "count", actor.getHand().size()));

        // 9. 필드 정리 — Phase 2 (필드 락 만료)

        events.toAll(EventType.TURN_ENDED, payload("playerId", actor.getPlayerId(), "turnNumber", state.getTurnNumber()));

        if (!finishIfOver(state, events)) {
            PlayerState next = TargetResolver.nextAlive(state, state.getCurrentSeat(), state.getDirection());
            state.setCurrentSeat(next.getSeat());
            startTurn(state, events);
        }
    }

    private void eliminate(GameState state, EventSink events) {
        for (PlayerState p : state.getPlayers()) {
            if (p.alive() && p.getHp() <= 0) {
                p.setEliminated(true);
                p.setEliminatedAtTurn(state.getTurnNumber());
                p.getHand().clear();
                events.toAll(EventType.PLAYER_ELIMINATED,
                        payload("playerId", p.getPlayerId(), "turnNumber", state.getTurnNumber()));
            }
        }
    }

    /** 생존자가 1명 이하면 게임을 끝낸다 (PRD 7.10) */
    private boolean finishIfOver(GameState state, EventSink events) {
        List<PlayerState> alive = state.alivePlayers();
        if (alive.size() > 1) {
            return false;
        }
        state.setStatus(GameStatus.FINISHED);
        state.setWinnerIds(alive.stream().map(PlayerState::getPlayerId).toList());
        state.setTurnDeadlineEpochMs(0);
        events.toAll(EventType.GAME_ENDED, payload(
                "winnerIds", List.copyOf(state.getWinnerIds()),
                "draw", alive.isEmpty(),
                "ranking", ranking(state)));
        return true;
    }

    /** 생존자 1위, 나머지는 탈락 역순. 같은 정산에서 탈락한 사람은 공동 순위 */
    static List<Map<String, Object>> ranking(GameState state) {
        List<PlayerState> sorted = new ArrayList<>(state.getPlayers());
        sorted.sort(Comparator.comparingInt(TurnResolver::rankKey).reversed());
        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 0;
        int prevKey = Integer.MIN_VALUE;
        for (int i = 0; i < sorted.size(); i++) {
            PlayerState p = sorted.get(i);
            int key = rankKey(p);
            if (key != prevKey) {
                rank = i + 1;
                prevKey = key;
            }
            result.add(payload("playerId", p.getPlayerId(), "nickname", p.getNickname(), "rank", rank));
        }
        return result;
    }

    private static int rankKey(PlayerState p) {
        return p.alive() ? Integer.MAX_VALUE : p.getEliminatedAtTurn();
    }

    // ------------------------------------------------------------------
    // 턴 시작, 드로우, 제출 가능 여부
    // ------------------------------------------------------------------

    public void startTurn(GameState state, EventSink events) {
        state.setTurnNumber(state.getTurnNumber() + 1);
        state.setTurnDeadlineEpochMs(clock.millis() + state.getSettings().turnTimeSeconds() * 1000L);
        PlayerState current = state.currentPlayer();
        events.toAll(EventType.TURN_STARTED, payload(
                "playerId", current.getPlayerId(),
                "turnNumber", state.getTurnNumber(),
                "deadlineEpochMs", state.getTurnDeadlineEpochMs()));
        events.toPlayer(current.getPlayerId(), EventType.PLAYABILITY_UPDATED,
                payload("cards", playabilityOf(state, current)));
    }

    /** 7.9 드로우: 손패 한도까지 가중치 추첨 */
    public void drawUpTo(GameState state, PlayerState player) {
        while (player.getHand().size() < player.getHandLimit()) {
            CardDefinition def = pack.cardForRoll(GameRng.nextInt(state, pack.totalWeight()));
            player.getHand().add(new CardInstance(state.newInstanceId(), def.id()));
        }
    }

    public List<CardPlayabilityView> playabilityOf(GameState state, PlayerState player) {
        List<CardPlayabilityView> result = new ArrayList<>();
        for (CardInstance inst : player.getHand()) {
            Playability p = playability.check(state, player, pack.card(inst.cardId()), null);
            result.add(new CardPlayabilityView(inst.instanceId(), p.playable(), p.code(), p.reason(), p.message()));
        }
        return result;
    }
}
