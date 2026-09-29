package com.cardbattle.engine;

import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.pack.PackFormatException;
import com.cardbattle.engine.pack.PackParser;
import com.cardbattle.engine.pack.PackValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PRD 8.6 카드팩 검증. 서버가 JSON을 Map으로 읽어 넘기는 상황을 그대로 흉내 낸다. */
class PackValidationTest {

    private final PackValidator validator = new PackValidator(EffectRegistry.defaults());

    private static Map<String, Object> meta() {
        return map("code", "demo", "name", "데모", "version", 1, "visibility", "PUBLIC", "ruleMode", "SC1");
    }

    private static Map<String, Object> attack(String id, int attack) {
        return map("id", id, "name", id, "category", "ATTACK", "attack", attack, "description", "공격", "weight", 5);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private List<String> validate(Map<String, Object>... cards) {
        CardPack pack = PackParser.parse(meta(), new ArrayList<>(List.of(cards)));
        return validator.validate(pack);
    }

    @Test
    @DisplayName("올바른 팩은 오류가 없다")
    void validPackPasses() {
        List<String> errors = validate(
                attack("demo.jab", 10),
                map("id", "demo.snipe", "name", "저격", "category", "SUPPORT", "targeting", "CHOSEN_OTHER",
                        "effects", List.of(map("type", "TRANSFER_ACCUMULATED", "to", "CHOSEN", "immediate", true)),
                        "description", "누적 데미지를 고른 사람에게", "weight", 1),
                map("id", "demo.dice", "name", "주사위", "category", "ATTACK", "attackRange", List.of(5, 30),
                        "conditions", List.of(map("type", "ANY", "of", List.of(
                                map("type", "SELF_HP_LTE", "value", 100),
                                map("type", "ACCUMULATED_ZERO")))),
                        "description", "5~30", "weight", 2));
        assertEquals(List.of(), errors);
    }

    @Test
    @DisplayName("오타 필드는 형식 오류로 거부된다")
    void unknownFieldRejected() {
        Map<String, Object> card = attack("demo.jab", 10);
        card.put("atack", 10);
        PackFormatException e = assertThrows(PackFormatException.class, () -> validate(card));
        assertTrue(e.errors().get(0).contains("atack"));
    }

    @Test
    @DisplayName("지원하지 않는 효과 타입은 거부된다")
    void unknownEffectTypeRejected() {
        Map<String, Object> card = attack("demo.jab", 10);
        card.put("effects", List.of(map("type", "TELEPORT")));
        List<String> errors = validate(card);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("TELEPORT"));
    }

    @Test
    @DisplayName("CHOSEN 효과를 쓰면서 targeting이 없으면 거부된다")
    void chosenWithoutTargetingRejected() {
        Map<String, Object> card = map("id", "demo.poke", "name", "찌르기", "category", "BENEFIT",
                "effects", List.of(map("type", "DAMAGE", "target", "CHOSEN", "amount", 10)),
                "description", "대상에게 10", "weight", 1);
        assertTrue(validate(card).get(0).contains("targeting"));
    }

    @Test
    @DisplayName("다음 차례가 아닌 대상에게 '넘기기'는 거부된다")
    void nonImmediateTransferMustGoToNext() {
        Map<String, Object> card = map("id", "demo.back", "name", "되넘기기", "category", "SUPPORT",
                "effects", List.of(map("type", "TRANSFER_ACCUMULATED", "to", "PREV")),
                "description", "이전 사람에게", "weight", 1);
        assertTrue(validate(card).get(0).contains("NEXT"));
    }

    @Test
    @DisplayName("공격 카드에 공격력이 없으면 거부된다")
    void attackCardNeedsAttack() {
        Map<String, Object> card = attack("demo.jab", 10);
        card.remove("attack");
        assertTrue(validate(card).get(0).contains("attack"));
    }

    @Test
    @DisplayName("중복 ID와 팩 코드 접두사 누락을 잡는다")
    void duplicateAndPrefix() {
        List<String> errors = validate(attack("demo.jab", 10), attack("demo.jab", 20), attack("jab", 5));
        assertTrue(errors.stream().anyMatch(e -> e.contains("중복")));
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("jab.id")));
    }

    @Test
    @DisplayName("아직 지원하지 않는 발동 시점은 거부된다")
    void unsupportedTimingRejected() {
        Map<String, Object> card = attack("demo.jab", 10);
        card.put("effects", List.of(map("type", "HEAL", "timing", "ON_NEXT_TURN", "target", "SELF", "amount", 5)));
        assertTrue(validate(card).get(0).contains("ON_NEXT_TURN"));
    }

    @Test
    @DisplayName("팩에 없는 카드를 참조하는 조건은 거부된다")
    void conditionReferencesUnknownCard() {
        Map<String, Object> card = attack("demo.jab", 10);
        card.put("conditions", List.of(map("type", "FIELD_HAS_CARD", "cardId", "demo.ghost")));
        assertTrue(validate(card).get(0).contains("demo.ghost"));
    }

    @Test
    @DisplayName("드로우할 수 있는 카드가 없으면 거부된다")
    void zeroTotalWeightRejected() {
        Map<String, Object> card = attack("demo.jab", 10);
        card.put("weight", 0);
        assertTrue(validate(card).stream().anyMatch(e -> e.contains("드로우")));
    }
}
