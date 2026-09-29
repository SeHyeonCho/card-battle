package com.cardbattle.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * application.yml 의 app.* 설정.
 *
 * @param accessCode     방을 만들 때 필요한 서버 접근 코드 (FR-AUTH-02)
 * @param adminKey       카드팩 import 관리자 키
 * @param allowedOrigins WebSocket 접속을 허용할 Origin 패턴
 * @param sessionTtl     게스트 세션 유지 시간
 * @param packs          서버 시작 시 자동으로 불러올 카드팩 폴더
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String accessCode,
        String adminKey,
        List<String> allowedOrigins,
        Duration sessionTtl,
        Packs packs) {

    public record Packs(List<String> autoImportDirs) {
    }
}
