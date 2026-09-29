package com.cardbattle.server.pack;

import com.cardbattle.server.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 카드팩 폴더에 딸린 효과음. 폴더 구조:
 * <pre>
 *   assets/sounds/manifest.json   { "카드 ID": "파일 이름.mp3" }
 *   assets/sounds/*.mp3
 * </pre>
 * 원작 팩의 소리는 로컬 전용(gitignore)이며, 대응표에 적힌 파일만 내보낸다.
 */
@Component
public class PackAssets {

    private static final Logger log = LoggerFactory.getLogger(PackAssets.class);
    private static final Pattern FILE_NAME = Pattern.compile("[a-z0-9_-]+\\.mp3");

    private record Sounds(Path dir, Map<String, String> byCard) {
    }

    private final Json json;
    private final Map<String, Sounds> packs = new ConcurrentHashMap<>();

    public PackAssets(Json json) {
        this.json = json;
    }

    /** 카드팩 폴더에 효과음 대응표가 있으면 등록한다. 없으면 아무 일도 없다 */
    public void register(String packCode, Path packDir) {
        Path dir = packDir.resolve("assets").resolve("sounds");
        Path manifest = dir.resolve("manifest.json");
        if (!Files.isRegularFile(manifest)) {
            return;
        }
        try {
            Map<String, String> byCard = new LinkedHashMap<>();
            json.readMap(Files.readString(manifest)).forEach((cardId, file) -> {
                String name = String.valueOf(file);
                if (FILE_NAME.matcher(name).matches() && Files.isRegularFile(dir.resolve(name))) {
                    byCard.put(cardId, name);
                } else {
                    log.warn("카드팩 {} 효과음 무시: {} → {}", packCode, cardId, name);
                }
            });
            packs.put(packCode, new Sounds(dir, byCard));
            log.info("카드팩 {} 효과음 {}개 등록", packCode, byCard.size());
        } catch (IOException | RuntimeException e) {
            log.error("카드팩 {} 효과음 대응표를 읽지 못했습니다: {}", packCode, manifest, e);
        }
    }

    /** 카드 ID → 파일 이름 */
    public Map<String, String> sounds(String packCode) {
        Sounds s = packs.get(packCode);
        return s == null ? Map.of() : s.byCard();
    }

    /** 대응표에 있는 파일일 때만 경로를 돌려준다 */
    public Optional<Path> soundFile(String packCode, String fileName) {
        Sounds s = packs.get(packCode);
        if (s == null || !s.byCard().containsValue(fileName)) {
            return Optional.empty();
        }
        return Optional.of(s.dir().resolve(fileName));
    }
}
