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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/packs/{code}/sounds?gameId=…          카드 ID → 효과음 주소 (없으면 빈 객체)
 * GET /api/packs/{code}/sounds/{file}?exp&sig    효과음 파일 (대응표에 있는 것만)
 * GET /api/packs/{code}/images?gameId=…          카드 ID → 카드 그림 주소 (없으면 빈 객체)
 * GET /api/packs/{code}/images/{file}?exp&sig    카드 그림 파일 (대응표에 있는 것만)
 * 비공개 팩은 목록을 그 팩으로 게임 중인 참가자(X-Session-Token)에게만 주고, 파일은 서명 주소로만 준다 ({@link PackAssetAccess}).
 */
@RestController
@RequestMapping("/api/packs/{code}/{kind:sounds|images}")
public class PackAssetController {

    private final PackAssets assets;
    private final PackAssetAccess access;

    public PackAssetController(PackAssets assets, PackAssetAccess access) {
        this.assets = assets;
        this.access = access;
    }

    @GetMapping
    public Map<String, String> list(@PathVariable String code, @PathVariable String kind,
                                    @RequestHeader(value = "X-Session-Token", required = false) String token,
                                    @RequestParam(required = false) String gameId) {
        Kind k = kind(kind);
        Map<String, String> files = assets.files(code, k);
        if (files.isEmpty()) {
            return Map.of();
        }
        access.requireViewer(code, token, gameId);
        Map<String, String> urls = new LinkedHashMap<>();
        files.forEach((cardId, file) ->
                urls.put(cardId, "/api/packs/" + code + "/" + k.path() + "/" + file + access.signedQuery(code, k, file)));
        return urls;
    }

    @GetMapping("/{file:.+}")
    public ResponseEntity<Resource> file(@PathVariable String code, @PathVariable String kind, @PathVariable String file,
                                         @RequestParam(required = false) Long exp, @RequestParam(required = false) String sig) {
        Kind k = kind(kind);
        if (!access.canDownload(code, k, file, exp, sig)) {
            throw ApiException.forbidden("ASSET_FORBIDDEN", "이 카드팩으로 게임 중인 참가자만 받을 수 있습니다");
        }
        CacheControl cache = access.isPublic(code)
                ? CacheControl.maxAge(Duration.ofDays(1))
                : CacheControl.maxAge(Duration.ofDays(1)).cachePrivate();
        return assets.file(code, k, file)
                .map(path -> ResponseEntity.ok()
                        .contentType(mediaType(file))
                        .cacheControl(cache)
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
