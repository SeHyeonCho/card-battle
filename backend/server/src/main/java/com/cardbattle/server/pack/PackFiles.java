package com.cardbattle.server.pack;

import com.cardbattle.server.common.Json;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 카드팩 폴더를 읽는다 (PRD 8.6).
 * <pre>
 * packs/sample/
 *   pack.json        팩 정보
 *   cards/*.json     카드 배열 (파일 이름 순서로 읽는다)
 * </pre>
 */
@Component
public class PackFiles {

    public record RawPack(Map<String, Object> meta, List<Map<String, Object>> cards) {
    }

    private final Json json;

    public PackFiles(Json json) {
        this.json = json;
    }

    public RawPack read(Path dir) throws IOException {
        Map<String, Object> meta = json.readMap(Files.readString(dir.resolve("pack.json")));
        List<Map<String, Object>> cards = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir.resolve("cards"))) {
            List<Path> sorted = files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
            for (Path file : sorted) {
                cards.addAll(json.readListOfMaps(Files.readString(file)));
            }
        }
        return new RawPack(meta, cards);
    }
}
