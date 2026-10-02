package com.cardbattle.server.room;

import com.cardbattle.server.common.AccessGuard;
import com.cardbattle.server.common.ApiException;
import com.cardbattle.server.common.Json;
import com.cardbattle.server.config.AppProperties;
import com.cardbattle.server.game.GameService;
import com.cardbattle.server.pack.PackCatalog;
import com.cardbattle.server.pack.PackRepository.PackRow;
import com.cardbattle.server.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 대기실 설정 변경·강퇴 (PRD FR-ROOM-05)와 게임 중 자리 비움 강퇴의 방 쪽 처리.
 * Redis·STOMP 없이 돌리려고 저장소·메시지·게임 서비스를 메모리 가짜로 바꿔 끼운다.
 */
class RoomServiceTest {

    private static final Json JSON = new Json(JsonMapper.builder().build());

    /** Redis 대신 메모리. 읽을 때마다 JSON으로 복사해서, save 하지 않은 변경은 남지 않는다 (Redis와 같다) */
    private static final class MemoryRooms extends RoomRepository {
        private final Map<String, String> byId = new HashMap<>();
        private final Map<String, String> byInvite = new HashMap<>();

        MemoryRooms() {
            super(null, JSON);
        }

        @Override
        public void save(Room room) {
            byId.put(room.getRoomId(), JSON.write(room));
            byInvite.put(room.getInviteCode(), room.getRoomId());
        }

        @Override
        public Optional<Room> find(String roomId) {
            return Optional.ofNullable(byId.get(roomId)).map(v -> JSON.read(v, Room.class));
        }

        @Override
        public Optional<Room> findByInvite(String inviteCode) {
            return Optional.ofNullable(byInvite.get(inviteCode)).flatMap(this::find);
        }

        @Override
        public boolean inviteTaken(String inviteCode) {
            return byInvite.containsKey(inviteCode);
        }

        @Override
        public void delete(Room room) {
            byId.remove(room.getRoomId());
            byInvite.remove(room.getInviteCode());
        }
    }

    /** 보낸 방 메시지를 모아 둔다 */
    private static final class Sent extends SimpMessagingTemplate {
        final List<Room> rooms = new ArrayList<>();

        Sent() {
            super((message, timeout) -> true);
        }

        @Override
        public void convertAndSend(String destination, Object payload) {
            if (payload instanceof Map<?, ?> m && m.get("room") instanceof Room room) {
                rooms.add(JSON.read(JSON.write(room), Room.class));
            }
        }
    }

    /** 게임 서비스 흉내: 게임을 만들고, 강퇴 결과는 테스트가 정한다 */
    private static final class FakeGames extends GameService {
        boolean kickAccepted = true;
        final List<String> kicked = new ArrayList<>();

        FakeGames() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public String createGame(Room room) {
            return "g_test";
        }

        @Override
        public boolean kick(String gameId, String requesterId, String targetId) {
            if (kickAccepted) {
                kicked.add(targetId);
            }
            return kickAccepted;
        }
    }

    private static final class FakePacks extends PackCatalog {
        private final Set<String> codes = Set.of("sample", "other");

        FakePacks() {
            super(null, null);
        }

        @Override
        public Optional<PackRow> latest(String code) {
            return codes.contains(code) ? Optional.of(new PackRow(1, code, code, "PUBLIC", "SC1", 1)) : Optional.empty();
        }
    }

    private static final AppProperties PROPS =
            new AppProperties("", null, List.of(), null, null, new AppProperties.Rooms(List.of("sample")));

    /** 접근 코드 검사를 하지 않는다 (Redis에 실패 횟수를 세지 않도록) */
    private static final class OpenGuard extends AccessGuard {
        OpenGuard() {
            super(null, PROPS);
        }

        @Override
        public void requireAccessCode(String code, String clientKey) {
        }
    }

    private static final Session HOST = new Session("t1", "host", "방장");
    private static final Session GUEST = new Session("t2", "guest", "손님");
    private static final Session OTHER = new Session("t3", "other", "셋째");

    private final MemoryRooms repo = new MemoryRooms();
    private final Sent sent = new Sent();
    private final FakeGames games = new FakeGames();
    private final RoomService rooms = new RoomService(repo, games, new FakePacks(), new OpenGuard(), sent, PROPS);
    private Room room;

    @BeforeEach
    void setUp() {
        room = rooms.create(HOST, new RoomSettings.Request(4, null, 200, 500, null, 25), null, "local");
        rooms.join(GUEST, room.getInviteCode());
        rooms.join(OTHER, room.getInviteCode());
        rooms.setReady(room.getRoomId(), GUEST.playerId(), true);
    }

    private Room saved() {
        return repo.find(room.getRoomId()).orElseThrow();
    }

    private static RoomSettings.Request settings(Integer maxPlayers, Integer startingHp, Integer turnTime) {
        return new RoomSettings.Request(maxPlayers, null, startingHp, null, null, turnTime);
    }

    private static String code(Runnable action) {
        return assertThrows(ApiException.class, action::run).code();
    }

    // ------------------------------------------------------------------
    // 설정 변경
    // ------------------------------------------------------------------

    @Test
    @DisplayName("방장이 설정을 바꾸면 준 값만 바뀌고, 참가자 준비가 풀리고, 모두에게 알린다")
    void hostUpdatesSettings() {
        int before = sent.rooms.size();

        rooms.updateSettings(room.getRoomId(), HOST.playerId(), settings(6, 300, null));

        RoomSettings s = saved().getSettings();
        assertEquals(6, s.maxPlayers());
        assertEquals(300, s.startingHp());
        assertEquals(25, s.turnTimeSeconds(), "비운 값은 그대로");
        assertEquals("sample", s.packCode());
        assertFalse(saved().member(GUEST.playerId()).isReady());
        assertEquals(before + 1, sent.rooms.size());
        assertEquals(6, sent.rooms.get(sent.rooms.size() - 1).getSettings().maxPlayers());
    }

    @Test
    @DisplayName("같은 값으로 바꾸면 준비는 그대로 둔다")
    void sameSettingsKeepReady() {
        rooms.updateSettings(room.getRoomId(), HOST.playerId(), settings(4, 200, 25));
        assertTrue(saved().member(GUEST.playerId()).isReady());
    }

    @Test
    @DisplayName("방장이 아니면 설정을 바꿀 수 없다")
    void onlyHostUpdatesSettings() {
        assertEquals("NOT_HOST", code(() -> rooms.updateSettings(room.getRoomId(), GUEST.playerId(), settings(6, null, null))));
        assertEquals(4, saved().getSettings().maxPlayers());
    }

    @Test
    @DisplayName("범위를 벗어난 값, 지금 인원보다 적은 최대 인원, 없는 카드팩은 거부한다")
    void rejectsInvalidSettings() {
        String id = room.getRoomId();
        assertEquals("INVALID_SETTINGS", code(() -> rooms.updateSettings(id, HOST.playerId(), settings(7, null, null))));
        assertEquals("INVALID_SETTINGS", code(() -> rooms.updateSettings(id, HOST.playerId(), settings(null, 50, null))));
        assertEquals("INVALID_SETTINGS", code(() -> rooms.updateSettings(id, HOST.playerId(), settings(null, null, 30))));
        assertEquals("INVALID_SETTINGS", code(() -> rooms.updateSettings(id, HOST.playerId(), settings(2, null, null))),
                "3명이 있는데 최대 2명으로 줄일 수 없다");
        assertEquals("INVALID_SETTINGS", code(() -> rooms.updateSettings(id, HOST.playerId(), null)));
        assertEquals("PACK_NOT_FOUND", code(() -> rooms.updateSettings(id, HOST.playerId(),
                new RoomSettings.Request(null, "nope", null, null, null, null))));
        assertEquals(new RoomSettings(4, "sample", 200, 500, 5, 25), saved().getSettings(), "거부되면 아무것도 안 바뀐다");

        rooms.updateSettings(id, HOST.playerId(), new RoomSettings.Request(3, "other", null, null, null, null));
        assertEquals("other", saved().getSettings().packCode());
        assertEquals(3, saved().getSettings().maxPlayers(), "지금 인원과 같게는 줄일 수 있다");
    }

    @Test
    @DisplayName("게임 중에는 설정을 바꿀 수 없다")
    void noSettingsInGame() {
        rooms.setReady(room.getRoomId(), OTHER.playerId(), true);
        rooms.start(room.getRoomId(), HOST.playerId());
        assertEquals("GAME_IN_PROGRESS", code(() -> rooms.updateSettings(room.getRoomId(), HOST.playerId(), settings(6, null, null))));
    }

    // ------------------------------------------------------------------
    // 강퇴
    // ------------------------------------------------------------------

    @Test
    @DisplayName("대기실 강퇴: 방에서 빠지고, 모두에게 알리고, 같은 방에 다시 들어올 수 없다")
    void lobbyKick() {
        rooms.kick(room.getRoomId(), HOST.playerId(), GUEST.playerId());

        assertNull(saved().member(GUEST.playerId()));
        assertEquals(List.of(GUEST.playerId()), saved().getKickedIds());
        Room last = sent.rooms.get(sent.rooms.size() - 1);
        assertNull(last.member(GUEST.playerId()), "강퇴된 사람 화면은 목록에서 자신이 빠진 것을 보고 나간다");
        assertTrue(games.kicked.isEmpty(), "대기실 강퇴는 게임과 상관없다");
        assertEquals("KICKED", code(() -> rooms.join(GUEST, room.getInviteCode())));
        assertNotNull(rooms.join(new Session("t4", "new", "새손님"), room.getInviteCode()), "다른 사람은 들어올 수 있다");
    }

    @Test
    @DisplayName("방장이 아니거나, 자기 자신이거나, 방에 없는 사람이면 강퇴할 수 없다")
    void kickRules() {
        String id = room.getRoomId();
        assertEquals("NOT_HOST", code(() -> rooms.kick(id, GUEST.playerId(), OTHER.playerId())));
        assertEquals("INVALID_TARGET", code(() -> rooms.kick(id, HOST.playerId(), HOST.playerId())));
        assertEquals("INVALID_TARGET", code(() -> rooms.kick(id, HOST.playerId(), null)));
        assertEquals("NOT_MEMBER", code(() -> rooms.kick(id, HOST.playerId(), "nobody")));
        assertEquals(3, saved().getMembers().size());
    }

    @Test
    @DisplayName("게임 중 강퇴: 게임이 받아들이면(자리 비움) 방에서도 빠지고 다시 못 들어온다")
    void inGameKick() {
        rooms.setReady(room.getRoomId(), OTHER.playerId(), true);
        rooms.start(room.getRoomId(), HOST.playerId());

        rooms.kick(room.getRoomId(), HOST.playerId(), OTHER.playerId());

        assertEquals(List.of(OTHER.playerId()), games.kicked);
        assertNull(saved().member(OTHER.playerId()));
        assertTrue(saved().kicked(OTHER.playerId()));
    }

    @Test
    @DisplayName("게임 중 강퇴를 게임이 거절하면(자리 비움이 아님) 방은 그대로다")
    void inGameKickRejected() {
        rooms.setReady(room.getRoomId(), OTHER.playerId(), true);
        rooms.start(room.getRoomId(), HOST.playerId());
        games.kickAccepted = false;

        rooms.kick(room.getRoomId(), HOST.playerId(), OTHER.playerId());

        assertNotNull(saved().member(OTHER.playerId()));
        assertFalse(saved().kicked(OTHER.playerId()));
    }

    @Test
    @DisplayName("강퇴 목록은 저장했다 읽어도 남는다 (Redis JSON)")
    void kickedIdsSurviveJson() {
        rooms.kick(room.getRoomId(), HOST.playerId(), GUEST.playerId());
        String json = JSON.write(saved());
        assertTrue(json.contains("\"kickedIds\""));
        assertFalse(json.contains("\"kicked\""), "계산용 메서드가 JSON 속성으로 새지 않는다");
        assertTrue(JSON.read(json, Room.class).kicked(GUEST.playerId()));
    }
}
