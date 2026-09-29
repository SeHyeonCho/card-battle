package com.cardbattle.engine.pack;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.card.ConditionSpec;
import com.cardbattle.engine.card.EffectSpec;
import com.cardbattle.engine.card.Timing;
import com.cardbattle.engine.effect.EffectHandler;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.rules.ConditionEvaluator;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 카드팩 의미 검사 (PRD 8.6, FR-OPS-02). 오류가 하나라도 있으면 팩 전체를 거부해야 한다.
 */
public final class PackValidator {

    private static final Pattern PACK_CODE = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Set<Timing> SUPPORTED_TIMINGS = EnumSet.of(Timing.ON_PLAY, Timing.ON_RECEIVE,
            Timing.ON_TURN_END);

    private final EffectRegistry effects;

    public PackValidator(EffectRegistry effects) {
        this.effects = effects;
    }

    public List<String> validate(CardPack pack) {
        List<String> errors = new ArrayList<>();
        if (pack.code() == null || !PACK_CODE.matcher(pack.code()).matches()) {
            errors.add("pack.code: 영문 소문자·숫자·_·- 로 된 64자 이하여야 합니다");
        }
        if (pack.version() < 1) {
            errors.add("pack.version: 1 이상이어야 합니다");
        }
        if (!Set.of("PUBLIC", "PRIVATE").contains(pack.visibility())) {
            errors.add("pack.visibility: PUBLIC 또는 PRIVATE 여야 합니다");
        }
        if (!"SC1".equals(pack.ruleMode())) {
            errors.add("pack.ruleMode: 현재는 SC1만 지원합니다");
        }
        if (pack.cards().isEmpty()) {
            errors.add("pack: 카드가 하나도 없습니다");
        } else if (pack.totalWeight() <= 0) {
            errors.add("pack: weight가 1 이상인 카드가 하나 이상 있어야 합니다 (드로우 불가)");
        }

        ConditionEvaluator conditions = new ConditionEvaluator(pack);
        Set<String> seen = new HashSet<>();
        for (CardDefinition card : pack.cards()) {
            String path = card.id() == null ? "card(?)" : card.id();
            if (!seen.add(card.id())) {
                errors.add(path + ": 카드 ID가 중복됩니다");
            }
            validateCard(pack, card, path, conditions, errors);
        }
        return errors;
    }

    private void validateCard(CardPack pack, CardDefinition card, String path, ConditionEvaluator conditions,
                              List<String> errors) {
        if (pack.code() != null && card.id() != null && !card.id().startsWith(pack.code() + ".")) {
            errors.add(path + ".id: '" + pack.code() + ".' 으로 시작해야 합니다 (예: " + pack.code() + ".jab)");
        }
        if (card.name() == null || card.name().isBlank()) {
            errors.add(path + ".name: 비어 있습니다");
        }
        if (card.description() == null || card.description().isBlank()) {
            errors.add(path + ".description: 비어 있습니다");
        }
        if (card.weight() < 0) {
            errors.add(path + ".weight: 0 이상이어야 합니다");
        }

        boolean hasFixed = card.attack() != null;
        boolean hasRange = card.randomAttack();
        if (card.attackCard()) {
            if (hasFixed == hasRange) {
                errors.add(path + ": 공격 카드는 attack 또는 attackRange 중 하나만 있어야 합니다");
            } else if (hasFixed && card.attack() < 1) {
                errors.add(path + ".attack: 1 이상이어야 합니다");
            } else if (hasRange && (card.attackMin() < 1 || card.attackMax() < card.attackMin())) {
                errors.add(path + ".attackRange: 1 ≤ 최소 ≤ 최대 여야 합니다");
            }
        } else if (hasFixed || hasRange) {
            errors.add(path + ": 공격 카드가 아니면 attack / attackRange를 쓸 수 없습니다");
        }

        for (int i = 0; i < card.conditions().size(); i++) {
            errors.addAll(conditions.validate(card.conditions().get(i), path + ".conditions[" + i + "]"));
        }

        boolean usesChosen = false;
        for (int i = 0; i < card.effects().size(); i++) {
            EffectSpec e = card.effects().get(i);
            String ePath = path + ".effects[" + i + "]";
            EffectHandler handler = effects.handler(e.type());
            if (handler == null) {
                errors.add(ePath + ".type: 지원하지 않는 효과 '" + e.type() + "' (지원: " + effects.supportedTypes() + ")");
                continue;
            }
            if (!SUPPORTED_TIMINGS.contains(e.timing())) {
                errors.add(ePath + ".timing: '" + e.timing() + "' 은 아직 지원하지 않습니다");
            }
            errors.addAll(handler.validate(e, ePath));
            for (String key : e.params().keySet()) {
                if (!EffectRegistry.WHEN.equals(key) && !handler.params().contains(key)) {
                    errors.add(ePath + "." + key + ": " + e.type() + " 에서 쓸 수 없는 파라미터입니다 (지원: "
                            + handler.params() + ", when)");
                }
            }
            if (e.raw(EffectRegistry.WHEN) != null) {
                try {
                    ConditionSpec when = PackParser.condition(e.raw(EffectRegistry.WHEN), ePath + ".when");
                    errors.addAll(conditions.validate(when, ePath + ".when"));
                } catch (PackFormatException ex) {
                    errors.addAll(ex.errors());
                }
            }
            if ("CHOSEN".equals(e.str("target")) || "CHOSEN".equals(e.str("to"))) {
                usesChosen = true;
            }
        }
        if (usesChosen && !card.targeting().requiresChoice()) {
            errors.add(path + ".targeting: 효과가 CHOSEN 대상을 쓰면 CHOSEN_OTHER 또는 CHOSEN_ANY 여야 합니다");
        }
        if (!usesChosen && card.targeting().requiresChoice()) {
            errors.add(path + ".targeting: 대상을 고르지만 CHOSEN을 쓰는 효과가 없습니다");
        }
    }
}
