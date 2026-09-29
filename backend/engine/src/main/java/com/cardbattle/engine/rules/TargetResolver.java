package com.cardbattle.engine.rules;

import com.cardbattle.engine.state.GameRng;
import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 효과의 target / to 값을 실제 플레이어 목록으로 바꾼다 (PRD 8.4).
 * 항상 생존자만 대상이 된다.
 */
public final class TargetResolver {

    /**
     * 효과에서 쓸 수 있는 대상 이름. CURSED(저주받은 사람)·CASTER(저주를 건 사람)는
     * 저주 효과 안에서만 의미가 있고 TurnContext가 처리한다.
     */
    public static final Set<String> TARGETS = Set.of(
            "SELF", "CHOSEN", "NEXT", "PREV", "ALL", "ALL_OTHERS", "RANDOM_ANY", "RANDOM_OTHER", "TOP_HP",
            "CURSED", "CASTER");

    private TargetResolver() {
    }

    public static List<PlayerState> resolve(String target, GameState state, PlayerState actor, PlayerState chosen) {
        List<PlayerState> alive = state.alivePlayers();
        return switch (target) {
            case "SELF" -> actor.alive() ? List.of(actor) : List.of();
            case "CHOSEN" -> chosen != null && chosen.alive() ? List.of(chosen) : List.of();
            case "NEXT" -> single(nextAlive(state, actor.getSeat(), state.getDirection()));
            case "PREV" -> single(nextAlive(state, actor.getSeat(), -state.getDirection()));
            case "ALL" -> alive;
            case "ALL_OTHERS" -> alive.stream().filter(p -> p != actor).toList();
            case "RANDOM_ANY" -> randomOne(state, alive);
            case "RANDOM_OTHER" -> randomOne(state, alive.stream().filter(p -> p != actor).toList());
            case "TOP_HP" -> topHp(alive);
            default -> throw new IllegalArgumentException("unknown target: " + target);
        };
    }

    /** fromSeat 다음(방향 기준)으로 살아 있는 플레이어. 자기 자신만 남았으면 null */
    public static PlayerState nextAlive(GameState state, int fromSeat, int direction) {
        int n = state.getPlayers().size();
        for (int step = 1; step < n; step++) {
            int seat = Math.floorMod(fromSeat + direction * step, n);
            PlayerState p = state.getPlayers().get(seat);
            if (p.alive()) {
                return p;
            }
        }
        return null;
    }

    private static List<PlayerState> single(PlayerState p) {
        return p == null ? List.of() : List.of(p);
    }

    private static List<PlayerState> randomOne(GameState state, List<PlayerState> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        return List.of(candidates.get(GameRng.nextInt(state, candidates.size())));
    }

    private static List<PlayerState> topHp(List<PlayerState> alive) {
        int max = alive.stream().mapToInt(PlayerState::getHp).max().orElse(Integer.MIN_VALUE);
        List<PlayerState> result = new ArrayList<>();
        for (PlayerState p : alive) {
            if (p.getHp() == max) {
                result.add(p);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
