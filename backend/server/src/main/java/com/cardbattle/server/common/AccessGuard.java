package com.cardbattle.server.common;

import com.cardbattle.server.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * 서버 접근 코드와 관리자 키 검사.
 * 접근 코드를 5번 연속 틀리면 10분간 막는다 (FR-AUTH-02).
 * 접근 코드(app.access-code)를 비워 두면 누구나 방을 만들 수 있다 (로컬·비공개 플레이용).
 */
@Component
public class AccessGuard {

    private static final Logger log = LoggerFactory.getLogger(AccessGuard.class);

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_TIME = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final AppProperties props;

    public AccessGuard(StringRedisTemplate redis, AppProperties props) {
        this.redis = redis;
        this.props = props;
        if (!accessCodeRequired()) {
            log.warn("서버 접근 코드가 비어 있어 누구나 방을 만들 수 있습니다. 공개 배포 전에는 APP_ACCESS_CODE 를 설정하세요");
        }
    }

    public boolean accessCodeRequired() {
        return props.accessCode() != null && !props.accessCode().isBlank();
    }

    /** 접근 코드를 쓰지 않는 서버면 항상 참 */
    public boolean validAccessCode(String code) {
        if (!accessCodeRequired()) {
            return true;
        }
        return code != null && constantTimeEquals(code, props.accessCode());
    }

    /** @param clientKey 차단 기준 (보통 접속 IP) */
    public void requireAccessCode(String code, String clientKey) {
        if (!accessCodeRequired()) {
            return;
        }
        String key = "accesscode:fail:" + clientKey;
        String failures = redis.opsForValue().get(key);
        if (failures != null && Integer.parseInt(failures) >= MAX_FAILURES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCESS_CODE_LOCKED",
                    "접근 코드를 여러 번 틀려서 10분간 막혔습니다");
        }
        if (!validAccessCode(code)) {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, LOCK_TIME);
            }
            throw ApiException.forbidden("INVALID_ACCESS_CODE", "서버 접근 코드가 올바르지 않습니다");
        }
        redis.delete(key);
    }

    public void requireAdminKey(String key) {
        if (key == null || !constantTimeEquals(key, props.adminKey())) {
            throw ApiException.forbidden("INVALID_ADMIN_KEY", "관리자 키가 올바르지 않습니다");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
