package com.cardbattle.engine.view;

/** 모두에게 공개되는 플레이어 정보. 손패 내용은 들어가지 않는다. */
public record PlayerView(String playerId, String nickname, int seat, int hp, int hpCap, int handCount,
                         boolean eliminated) {
}
