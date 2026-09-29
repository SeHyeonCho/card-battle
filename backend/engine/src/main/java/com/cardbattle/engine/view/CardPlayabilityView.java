package com.cardbattle.engine.view;

import com.cardbattle.engine.result.PlayBlockReason;
import com.cardbattle.engine.result.RejectCode;

/** 손패 카드 한 장의 제출 가능 여부 (PRD FR-CARD-02). */
public record CardPlayabilityView(String instanceId, boolean playable, RejectCode code, PlayBlockReason reason,
                                  String message) {
}
