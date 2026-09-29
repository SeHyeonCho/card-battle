package com.cardbattle.server.session;

import com.cardbattle.server.common.ApiException;
import com.cardbattle.server.common.Json;
import com.cardbattle.server.config.AppProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** 게스트 세션을 Redis에 저장한다. 사용할 때마다 만료 시간이 연장된다. */
@Service
public class SessionService {

    private static final String KEY = "session:";

    private final StringRedisTemplate redis;
    private final Json json;
    private final AppProperties props;

    public SessionService(StringRedisTemplate redis, Json json, AppProperties props) {
        this.redis = redis;
        this.json = json;
        this.props = props;
    }

    public Session create(String nickname) {
        String name = nickname == null ? "" : nickname.strip();
        if (name.length() < 2 || name.length() > 12) {
            throw ApiException.badRequest("INVALID_NICKNAME", "닉네임은 2~12자여야 합니다");
        }
        String playerId = "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Session session = new Session(UUID.randomUUID().toString(), playerId, name);
        redis.opsForValue().set(KEY + session.token(), json.write(session), props.sessionTtl());
        return session;
    }

    public Optional<Session> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = redis.opsForValue().get(KEY + token);
        if (value == null) {
            return Optional.empty();
        }
        redis.expire(KEY + token, props.sessionTtl());
        return Optional.of(json.read(value, Session.class));
    }

    public Session require(String token) {
        return find(token).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION",
                "세션이 없거나 만료됐습니다. 닉네임을 다시 입력해 주세요"));
    }
}
