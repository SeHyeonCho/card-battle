package com.cardbattle.server.pack;

import com.cardbattle.engine.state.GameState;
import com.cardbattle.engine.state.PlayerState;
import com.cardbattle.server.common.ApiException;
import com.cardbattle.server.game.GameRepository;
import com.cardbattle.server.pack.PackAssets.Kind;
import com.cardbattle.server.pack.PackRepository.PackRow;
import com.cardbattle.server.session.Session;
import com.cardbattle.server.session.SessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 비공개 카드팩 그림·소리 접근 제한 (배포 시 원작 리소스 보호).
 * Redis 대신 메모리 가짜를 끼운다.
 */
class PackAssetAccessTest {

    private static final class FakePacks extends PackCatalog {
        FakePacks() {
            super(null, null);
        }

        @Override
        public Optional<PackRow> latest(String code) {
            return switch (code) {
                case "sample" -> Optional.of(new PackRow(1, code, code, "PUBLIC", "SC1", 1));
                case "original", "other" -> Optional.of(new PackRow(2, code, code, "PRIVATE", "SC1", 1));
                default -> Optional.empty();
            };
        }
    }

    private static final class FakeSessions extends SessionService {
        private final Map<String, Session> byToken = Map.of(
                "t_in", new Session("t_in", "p_in", "참가자"),
                "t_out", new Session("t_out", "p_out", "구경꾼"));

        FakeSessions() {
            super(null, null, null);
        }

        @Override
        public Optional<Session> find(String token) {
            return Optional.ofNullable(token == null ? null : byToken.get(token));
        }
    }

    private static final class FakeGames extends GameRepository {
        FakeGames() {
            super(null, null);
        }

        @Override
        public Optional<GameState> load(String gameId) {
            if (!gameId.startsWith("g_")) {
                return Optional.empty();
            }
            GameState state = new GameState();
            state.setGameId(gameId);
            state.setPackCode(gameId.equals("g_other") ? "other" : "original");
            PlayerState player = new PlayerState();
            player.setPlayerId("p_in");
            state.setPlayers(List.of(player));
            return Optional.of(state);
        }
    }

    private static PackAssetAccess access() {
        return new PackAssetAccess(new FakePacks(), new FakeSessions(), new FakeGames(), null) {
            @Override
            byte[] secret() {
                return "test-secret-test-secret-test-sec".getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    /** "?exp=…&sig=…" → [exp, sig] */
    private static String[] parse(String query) {
        String[] parts = query.substring(1).split("&");
        return new String[]{parts[0].substring(4), parts[1].substring(4)};
    }

    @Test
    @DisplayName("공개 팩은 세션 없이 목록·파일을 받는다")
    void publicPackIsOpen() {
        PackAssetAccess a = access();
        assertDoesNotThrow(() -> a.requireViewer("sample", null, null));
        assertEquals("", a.signedQuery("sample", Kind.SOUNDS, "x.mp3"));
        assertTrue(a.canDownload("sample", Kind.SOUNDS, "x.mp3", null, null));
    }

    @Test
    @DisplayName("비공개 팩 목록은 그 팩으로 게임 중인 참가자만 받는다")
    void privateListNeedsPlayerOfThatPack() {
        PackAssetAccess a = access();
        assertEquals("INVALID_SESSION", assertThrows(ApiException.class, () -> a.requireViewer("original", null, "g_1")).code());
        assertEquals("ASSET_FORBIDDEN", assertThrows(ApiException.class, () -> a.requireViewer("original", "t_out", "g_1")).code(),
                "게임 참가자가 아님");
        assertEquals("ASSET_FORBIDDEN", assertThrows(ApiException.class, () -> a.requireViewer("original", "t_in", "g_other")).code(),
                "다른 팩으로 하는 게임");
        assertEquals("ASSET_FORBIDDEN", assertThrows(ApiException.class, () -> a.requireViewer("original", "t_in", null)).code());
        assertDoesNotThrow(() -> a.requireViewer("original", "t_in", "g_1"));
    }

    @Test
    @DisplayName("비공개 팩 파일은 목록이 준 서명 주소로만 받는다")
    void privateFileNeedsValidSignature() {
        PackAssetAccess a = access();
        String[] q = parse(a.signedQuery("original", Kind.IMAGES, "one_punch.webp"));
        long exp = Long.parseLong(q[0]);
        assertTrue(a.canDownload("original", Kind.IMAGES, "one_punch.webp", exp, q[1]));

        assertFalse(a.canDownload("original", Kind.IMAGES, "one_punch.webp", null, null), "서명 없음");
        assertFalse(a.canDownload("original", Kind.IMAGES, "snipe.webp", exp, q[1]), "다른 파일에 같은 서명");
        assertFalse(a.canDownload("original", Kind.SOUNDS, "one_punch.webp", exp, q[1]), "다른 종류");
        assertFalse(a.canDownload("original", Kind.IMAGES, "one_punch.webp", exp + 1, q[1]), "만료 시각을 바꿈");
        long past = System.currentTimeMillis() / 1000 - 1;
        String[] expired = {String.valueOf(past), parse(a.signedQuery("original", Kind.IMAGES, "one_punch.webp"))[1]};
        assertFalse(a.canDownload("original", Kind.IMAGES, "one_punch.webp", past, expired[1]), "만료됨");
    }
}
