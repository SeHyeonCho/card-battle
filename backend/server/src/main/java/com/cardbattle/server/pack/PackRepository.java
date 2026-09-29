package com.cardbattle.server.pack;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 카드팩 DB 접근 (PRD 10.1).
 * 카드 정의 전체는 definition(JSONB)에 원본 JSON 그대로 저장하고, 조회용 컬럼만 따로 둔다.
 */
@Repository
public class PackRepository {

    public record PackRow(long id, String code, String name, String visibility, String ruleMode, int version) {
    }

    private final JdbcTemplate jdbc;

    public PackRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean exists(String code, int version) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM card_pack WHERE code = ? AND version = ?", Integer.class, code, version);
        return count != null && count > 0;
    }

    /** cardJsons는 pack.cards()와 같은 순서여야 한다 */
    public long insert(CardPack pack, List<String> cardJsons) {
        Long packId = jdbc.queryForObject(
                "INSERT INTO card_pack (code, name, visibility, rule_mode, version) VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, pack.code(), pack.name(), pack.visibility(), pack.ruleMode(), pack.version());
        for (int i = 0; i < pack.cards().size(); i++) {
            CardDefinition card = pack.cards().get(i);
            jdbc.update(
                    "INSERT INTO card (pack_id, code, name, category, weight, definition) "
                            + "VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))",
                    packId, card.id(), card.name(), card.category().name(), card.weight(), cardJsons.get(i));
        }
        return packId;
    }

    /** 코드별 최신 버전 */
    public List<PackRow> latestPacks() {
        return jdbc.query(
                "SELECT DISTINCT ON (code) id, code, name, visibility, rule_mode, version "
                        + "FROM card_pack ORDER BY code, version DESC",
                (rs, rowNum) -> new PackRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("visibility"), rs.getString("rule_mode"), rs.getInt("version")));
    }

    public Optional<PackRow> find(String code, int version) {
        List<PackRow> rows = jdbc.query(
                "SELECT id, code, name, visibility, rule_mode, version FROM card_pack WHERE code = ? AND version = ?",
                (rs, rowNum) -> new PackRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("visibility"), rs.getString("rule_mode"), rs.getInt("version")),
                code, version);
        return rows.stream().findFirst();
    }

    public List<String> cardDefinitions(long packId) {
        return jdbc.queryForList("SELECT definition::text FROM card WHERE pack_id = ? ORDER BY id", String.class,
                packId);
    }
}
