package com.cardbattle.server.pack;

import com.cardbattle.server.common.ApiException;
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
 */
@RestController
@RequestMapping("/api/packs/{code}/sounds")
public class PackAssetController {

    private final PackAssets assets;

    public PackAssetController(PackAssets assets) {
        this.assets = assets;
    }

    @GetMapping
    public Map<String, String> sounds(@PathVariable String code) {
        Map<String, String> urls = new LinkedHashMap<>();
        assets.sounds(code).forEach((cardId, file) -> urls.put(cardId, "/api/packs/" + code + "/sounds/" + file));
        return urls;
    }

    @GetMapping("/{file:.+}")
    public ResponseEntity<Resource> file(@PathVariable String code, @PathVariable String file) {
        return assets.soundFile(code, file)
                .map(path -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("audio/mpeg"))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
                        .body((Resource) new FileSystemResource(path)))
                .orElseThrow(() -> ApiException.notFound("SOUND_NOT_FOUND", "효과음이 없습니다"));
    }
}
