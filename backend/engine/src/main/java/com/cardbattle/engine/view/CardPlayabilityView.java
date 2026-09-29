package com.cardbattle.engine.view;

import com.cardbattle.engine.result.PlayBlockReason;
import com.cardbattle.engine.result.RejectCode;

/**
 * 손패 카드 한 장의 제출 가능 여부 (PRD FR-CARD-02).
 *
 * @param forcedTargetId 대상을 고르는 카드인데 대상이 이미 정해져 있으면 그 플레이어 ID
 *                       (멈춰! 저주: 항상 자기 자신). 이때 화면은 대상 선택 없이 바로 낸다. 그 외에는 null
 */
public record CardPlayabilityView(String instanceId, boolean playable, RejectCode code, PlayBlockReason reason,
                                  String message, String forcedTargetId) {
}
