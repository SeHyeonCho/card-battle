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
 * 카드팩 폴더에 딸린 카드별 효과음·그림. 폴더 구조:
 * <pre>
 *   assets/sounds/manifest.json   { "카드 ID": "파일 이름.mp3" }
 *   assets/sounds/*.mp3
 *   assets/images/cards/manifest.json   { "카드 ID": "파일 이름.webp|png" }
 *   assets/images/cards/*.webp|png
 * </pre>
 * 원작 팩의 리소스는 로컬 전용(gitignore)이며, 대응표에 적힌 파일만 내보낸다.
 */
@Component
public class PackAssets {

    /** 리소스 종류: 주소 이름, 팩 폴더 안의 위치, 허용하는 파일 이름 */
    public enum Kind {
        SOUNDS("sounds", Path.of("assets", "sounds"), Pattern.compile("[a-z0-9_-]+\\.mp3")),
        IMAGES("images", Path.of("assets", "images", "cards"), Pattern.compile("[a-z0-9_-]+\\.(png|webp)"));

        private final String path;
        private final Path dir;
        private final Pattern fileName;

        Kind(String path, Path dir, Pattern fileName) {
            this.path = path;
            this.dir = dir;
            this.fileName = fileName;
        }

        public String path() {
            return path;
        }

        public static Optional<Kind> of(String path) {
            for (Kind k : values()) {
                if (k.path.equals(path)) {
                    return Optional.of(k);
                }
            }
            return Optional.empty();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(PackAssets.class);

    private record Registered(Path dir, Map<String, String> byCard) {
    }

    private record Key(String packCode, Kind kind) {
    }

    private final Json json;
    private final Map<Key, Registered> packs = new ConcurrentHashMap<>();

    public PackAssets(Json json) {
        this.json = json;
    }

    /** 카드팩 폴더에 효과음·그림 대응표가 있으면 등록한다. 없으면 아무 일도 없다 */
    public void register(String packCode, Path packDir) {
        for (Kind kind : Kind.values()) {
            register(packCode, packDir, kind);
        }
    }

    private void register(String packCode, Path packDir, Kind kind) {
        Path dir = packDir.resolve(kind.dir);
        Path manifest = dir.resolve("manifest.json");
        if (!Files.isRegularFile(manifest)) {
            return;
        }
        try {
            Map<String, String> byCard = new LinkedHashMap<>();
            json.readMap(Files.readString(manifest)).forEach((cardId, file) -> {
                String name = String.valueOf(file);
                if (kind.fileName.matcher(name).matches() && Files.isRegularFile(dir.resolve(name))) {
                    byCard.put(cardId, name);
                } else {
                    log.warn("카드팩 {} {} 무시: {} → {}", packCode, kind.path, cardId, name);
                }
            });
            packs.put(new Key(packCode, kind), new Registered(dir, byCard));
            log.info("카드팩 {} {} {}개 등록", packCode, kind.path, byCard.size());
        } catch (IOException | RuntimeException e) {
            log.error("카드팩 {} {} 대응표를 읽지 못했습니다: {}", packCode, kind.path, manifest, e);
        }
    }

    /** 카드 ID → 파일 이름 */
    public Map<String, String> files(String packCode, Kind kind) {
        Registered f = packs.get(new Key(packCode, kind));
        return f == null ? Map.of() : f.byCard();
    }

    /** 대응표에 있는 파일일 때만 경로를 돌려준다 */
    public Optional<Path> file(String packCode, Kind kind, String fileName) {
        Registered f = packs.get(new Key(packCode, kind));
        if (f == null || !f.byCard().containsValue(fileName)) {
            return Optional.empty();
        }
        return Optional.of(f.dir().resolve(fileName));
    }
}
