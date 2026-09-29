package com.cardbattle.server.pack;

import com.cardbattle.engine.card.CardDefinition;
import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.pack.PackParser;
import com.cardbattle.server.common.ApiException;
import com.cardbattle.server.common.Json;
import com.cardbattle.server.pack.PackRepository.PackRow;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DB의 카드팩을 메모리에 캐시한다 (PRD 3.3).
 * 진행 중인 게임은 시작할 때의 팩 버전으로 끝까지 가므로 "코드:버전" 단위로 캐시한다.
 */
@Component
public class PackCatalog {

    private final PackRepository repository;
    private final Json json;
    private final Map<String, CardPack> cache = new ConcurrentHashMap<>();
    private volatile Map<String, PackRow> latest = Map.of();

    public PackCatalog(PackRepository repository, Json json) {
        this.repository = repository;
        this.json = json;
    }

    public void reload() {
        Map<String, PackRow> rows = new HashMap<>();
        for (PackRow row : repository.latestPacks()) {
            rows.put(row.code(), row);
            cache.put(key(row.code(), row.version()), load(row));
        }
        latest = Map.copyOf(rows);
    }

    public List<PackRow> list(boolean includePrivate) {
        return latest.values().stream()
                .filter(row -> includePrivate || "PUBLIC".equals(row.visibility()))
                .sorted(Comparator.comparing(PackRow::code))
                .toList();
    }

    public Optional<PackRow> latest(String code) {
        return Optional.ofNullable(latest.get(code));
    }

    public CardPack get(String code, int version) {
        return cache.computeIfAbsent(key(code, version), k -> repository.find(code, version)
                .map(this::load)
                .orElseThrow(() -> ApiException.notFound("PACK_NOT_FOUND", "카드팩이 없습니다: " + k)));
    }

    public CardPack latestPack(String code) {
        PackRow row = latest(code)
                .orElseThrow(() -> ApiException.notFound("PACK_NOT_FOUND", "카드팩이 없습니다: " + code));
        return get(row.code(), row.version());
    }

    private CardPack load(PackRow row) {
        List<CardDefinition> cards = repository.cardDefinitions(row.id()).stream()
                .map(json::readMap)
                .map(PackParser::parseCard)
                .toList();
        return new CardPack(row.code(), row.name(), row.version(), row.visibility(), row.ruleMode(), cards);
    }

    private static String key(String code, int version) {
        return code + ":" + version;
    }
}
