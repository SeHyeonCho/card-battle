package com.cardbattle.server.pack;

import com.cardbattle.server.common.ApiException;
import com.cardbattle.server.pack.PackAssets.Kind;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/packs/{code}/sounds          카드 ID → 효과음 주소 (없으면 빈 객체)
 * GET /api/packs/{code}/sounds/{file}   효과음 파일 (대응표에 있는 것만)
 * GET /api/packs/{code}/images          카드 ID → 카드 그림 주소 (없으면 빈 객체)
 * GET /api/packs/{code}/images/{file}   카드 그림 파일 (대응표에 있는 것만)
 */
@RestController
@RequestMapping("/api/packs/{code}/{kind:sounds|images}")
public class PackAssetController {

    private final PackAssets assets;

    public PackAssetController(PackAssets assets) {
        this.assets = assets;
    }

    @GetMapping
    public Map<String, String> list(@PathVariable String code, @PathVariable String kind) {
        Kind k = kind(kind);
        Map<String, String> urls = new LinkedHashMap<>();
        assets.files(code, k).forEach((cardId, file) ->
                urls.put(cardId, "/api/packs/" + code + "/" + k.path() + "/" + file));
        return urls;
    }

    @GetMapping("/{file:.+}")
    public ResponseEntity<Resource> file(@PathVariable String code, @PathVariable String kind, @PathVariable String file) {
        Kind k = kind(kind);
        return assets.file(code, k, file)
                .map(path -> ResponseEntity.ok()
                        .contentType(mediaType(file))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
                        .body((Resource) new FileSystemResource(path)))
                .orElseThrow(() -> ApiException.notFound("ASSET_NOT_FOUND", "파일이 없습니다"));
    }

    private static MediaType mediaType(String file) {
        String ext = file.substring(file.lastIndexOf('.') + 1);
        return switch (ext) {
            case "mp3" -> MediaType.parseMediaType("audio/mpeg");
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private static Kind kind(String path) {
        return Kind.of(path).orElseThrow(() -> ApiException.notFound("ASSET_NOT_FOUND", "파일이 없습니다"));
    }
}
