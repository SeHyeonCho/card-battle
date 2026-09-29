package com.cardbattle.server.pack;

import com.cardbattle.engine.card.CardPack;
import com.cardbattle.engine.effect.EffectRegistry;
import com.cardbattle.engine.pack.PackFormatException;
import com.cardbattle.engine.pack.PackParser;
import com.cardbattle.engine.pack.PackValidator;
import com.cardbattle.server.common.Json;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 카드팩 import (FR-OPS-01, 02). 형식·의미 검사를 모두 통과해야 저장한다.
 * 같은 코드·버전이 이미 있으면 건너뛴다 — 카드를 고쳤으면 pack.json의 version을 올린다.
 */
@Service
public class PackImportService {

    public enum Result {
        IMPORTED,
        ALREADY_EXISTS
    }

    private final PackRepository repository;
    private final Json json;
    private final PackValidator validator = new PackValidator(EffectRegistry.defaults());

    public PackImportService(PackRepository repository, Json json) {
        this.repository = repository;
        this.json = json;
    }

    @Transactional
    public Result importPack(Map<String, Object> meta, List<Map<String, Object>> cards) {
        CardPack pack = PackParser.parse(meta, cards);
        List<String> errors = validator.validate(pack);
        if (!errors.isEmpty()) {
            throw new PackFormatException(errors);
        }
        if (repository.exists(pack.code(), pack.version())) {
            return Result.ALREADY_EXISTS;
        }
        repository.insert(pack, cards.stream().map(json::write).toList());
        return Result.IMPORTED;
    }
}
