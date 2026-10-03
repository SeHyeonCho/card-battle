package com.cardbattle.server.pack;

import com.cardbattle.engine.state.GameState;
import com.cardbattle.server.common.ApiException;
import com.cardbattle.server.game.GameRepository;
import com.cardbattle.server.pack.PackAssets.Kind;
import com.cardbattle.server.session.Session;
import com.cardbattle.server.session.SessionService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 비공개 카드팩(원작 팩)의 그림·소리 접근 제한. 공개 팩은 누구나 받을 수 있다.
 * <ul>
 *   <li>목록(카드 ID → 주소): 그 팩으로 진행 중인 게임의 참가자만 받는다 (세션 토큰 + gameId)</li>
 *   <li>파일: 목록이 준 서명 주소(?exp=…&amp;sig=…)로만 받는다. &lt;img&gt;·오디오는 헤더를 못 붙이므로 서명을 주소에 넣는다</li>
 * </ul>
 * 서명 키는 Redis에 한 번 만들어 두어 서버를 다시 띄워도 이미 받은 주소가 계속 통한다.
 */
@Component
public class PackAssetAccess {

    /** 서명 주소 유효 시간 (한 판이 이보다 길지 않다) */
    static final Duration LINK_TTL = Duration.ofHours(12);
    private static final String SECRET_KEY = "asset:secret";

    private final PackCatalog catalog;
    private final SessionService sessions;
    private final GameRepository games;
    private final StringRedisTemplate redis;
    private volatile byte[] secret;

    public PackAssetAccess(PackCatalog catalog, SessionService sessions, GameRepository games, StringRedisTemplate redis) {
        this.catalog = catalog;
        this.sessions = sessions;
        this.games = games;
        this.redis = redis;
    }

    public boolean isPublic(String packCode) {
        return catalog.latest(packCode).map(row -> "PUBLIC".equals(row.visibility())).orElse(false);
    }

    /** 비공개 팩이면 요청한 사람이 그 팩으로 진행 중인 게임의 참가자인지 확인한다 */
    public void requireViewer(String packCode, String token, String gameId) {
        if (isPublic(packCode)) {
            return;
        }
        Session session = sessions.require(token);
        GameState state = gameId == null ? null : games.load(gameId).orElse(null);
        if (state == null || !packCode.equals(state.getPackCode()) || state.player(session.playerId()) == null) {
            throw ApiException.forbidden("ASSET_FORBIDDEN", "이 카드팩으로 게임 중인 참가자만 받을 수 있습니다");
        }
    }

    /** 비공개 팩 파일 주소에 붙일 서명 (공개 팩이면 빈 문자열) */
    public String signedQuery(String packCode, Kind kind, String file) {
        if (isPublic(packCode)) {
            return "";
        }
        long exp = System.currentTimeMillis() / 1000 + LINK_TTL.toSeconds();
        return "?exp=" + exp + "&sig=" + sign(packCode, kind, file, exp);
    }

    public boolean canDownload(String packCode, Kind kind, String file, Long exp, String sig) {
        if (isPublic(packCode)) {
            return true;
        }
        if (exp == null || sig == null || exp < System.currentTimeMillis() / 1000) {
            return false;
        }
        byte[] expected = sign(packCode, kind, file, exp).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String packCode, Kind kind, String file, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret(), "HmacSHA256"));
            byte[] digest = mac.doFinal((packCode + "/" + kind.path() + "/" + file + "/" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 서명 키 (Redis에 없으면 만든다). 테스트는 이것만 바꿔 끼운다 */
    byte[] secret() {
        byte[] s = secret;
        if (s == null) {
            byte[] fresh = new byte[32];
            new SecureRandom().nextBytes(fresh);
            redis.opsForValue().setIfAbsent(SECRET_KEY, HexFormat.of().formatHex(fresh));
            s = HexFormat.of().parseHex(redis.opsForValue().get(SECRET_KEY));
            secret = s;
        }
        return s;
    }
}
