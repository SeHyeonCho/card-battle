package com.cardbattle.server.room;

import com.cardbattle.server.common.AccessGuard;
import com.cardbattle.server.common.ApiException;
import com.cardbattle.server.config.AppProperties;
import com.cardbattle.server.game.GameFinishedEvent;
import com.cardbattle.server.game.GameService;
import com.cardbattle.server.pack.PackCatalog;
import com.cardbattle.server.session.Session;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * 방 생성·참가·준비·시작 (PRD 6.2).
 * Phase 1은 서버 1대 기준이라 방 단위 락을 JVM 안에서 건다.
 */
@Service
public class RoomService {

    /** 헷갈리는 글자(0/O, 1/I)를 뺀 초대 코드 문자 */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** 설정한 기본 팩이 하나도 없을 때 (공개 저장소에 항상 있는 팩) */
    private static final String FALLBACK_PACK = "sample";

    public record RoomSummary(String inviteCode, int memberCount, int maxPlayers, RoomStatus status,
                              String hostNickname) {
    }

    private final RoomRepository rooms;
    private final GameService games;
    private final PackCatalog packs;
    private final AccessGuard guard;
    private final SimpMessagingTemplate messaging;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public RoomService(RoomRepository rooms, GameService games, PackCatalog packs, AccessGuard guard,
                       SimpMessagingTemplate messaging, AppProperties props) {
        this.rooms = rooms;
        this.games = games;
        this.packs = packs;
        this.guard = guard;
        this.messaging = messaging;
        this.props = props;
    }

    public Room create(Session host, RoomSettings.Request request, String accessCode, String clientKey) {
        guard.requireAccessCode(accessCode, clientKey);
        RoomSettings settings = RoomSettings.from(request, defaultPack());
        packs.latest(settings.packCode())
                .orElseThrow(() -> ApiException.badRequest("PACK_NOT_FOUND", "카드팩이 없습니다: " + settings.packCode()));

        Room room = new Room();
        room.setRoomId("r_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        room.setInviteCode(newInviteCode());
        room.setHostId(host.playerId());
        room.setSettings(settings);
        room.setCreatedAt(System.currentTimeMillis());
        room.getMembers().add(new RoomMember(host.playerId(), host.nickname(), System.currentTimeMillis()));
        rooms.save(room);
        return room;
    }

    /** 설정 순서대로 불러와 있는 첫 팩 (원작 팩이 없는 곳에서는 샘플 팩) */
    private String defaultPack() {
        List<String> candidates = props.rooms() == null || props.rooms().defaultPacks() == null
                ? List.of() : props.rooms().defaultPacks();
        return candidates.stream().filter(code -> packs.latest(code).isPresent()).findFirst().orElse(FALLBACK_PACK);
    }

    public RoomSummary summary(String inviteCode) {
        Room room = byInvite(inviteCode);
        RoomMember host = room.member(room.getHostId());
        return new RoomSummary(room.getInviteCode(), room.getMembers().size(), room.getSettings().maxPlayers(),
                room.getStatus(), host == null ? null : host.getNickname());
    }

    /** 이미 참가한 사람이 다시 부르면 그대로 방을 돌려준다 (새로고침 복귀용) */
    public Room join(Session session, String inviteCode) {
        Room found = byInvite(inviteCode);
        return update(found.getRoomId(), room -> {
            if (room.member(session.playerId()) != null) {
                return;
            }
            if (room.getStatus() == RoomStatus.IN_GAME) {
                throw ApiException.conflict("GAME_IN_PROGRESS", "이미 게임이 진행 중인 방입니다");
            }
            if (room.getMembers().size() >= room.getSettings().maxPlayers()) {
                throw ApiException.conflict("ROOM_FULL", "방이 가득 찼습니다");
            }
            room.getMembers().add(new RoomMember(session.playerId(), session.nickname(), System.currentTimeMillis()));
        });
    }

    public Room setReady(String roomId, String playerId, boolean ready) {
        return update(roomId, room -> {
            RoomMember member = requireMember(room, playerId);
            requireLobby(room);
            member.setReady(ready);
        });
    }

    public Room start(String roomId, String playerId) {
        return update(roomId, room -> {
            requireMember(room, playerId);
            requireLobby(room);
            if (!room.hostedBy(playerId)) {
                throw ApiException.forbidden("NOT_HOST", "방장만 시작할 수 있습니다");
            }
            if (room.getMembers().size() < 2) {
                throw ApiException.badRequest("NOT_ENOUGH_PLAYERS", "2명 이상이어야 시작할 수 있습니다");
            }
            boolean allReady = room.getMembers().stream()
                    .allMatch(m -> m.isReady() || room.hostedBy(m.getPlayerId()));
            if (!allReady) {
                throw ApiException.badRequest("NOT_ALL_READY", "모두 준비해야 시작할 수 있습니다");
            }
            String gameId = games.createGame(room);
            room.setGameId(gameId);
            room.setStatus(RoomStatus.IN_GAME);
        });
    }

    /** 대기실에서 나가기. 게임 중 나가기는 Phase 1에서 지원하지 않는다 (시간 초과로 진행) */
    public void leave(String roomId, String playerId) {
        withLock(roomId, () -> {
            Room room = requireRoom(roomId);
            if (room.getStatus() != RoomStatus.LOBBY || room.member(playerId) == null) {
                return;
            }
            room.getMembers().removeIf(m -> m.getPlayerId().equals(playerId));
            if (room.getMembers().isEmpty()) {
                rooms.delete(room);
                return;
            }
            if (room.hostedBy(playerId)) {
                room.getMembers().stream()
                        .min(Comparator.comparingLong(RoomMember::getJoinedAt))
                        .ifPresent(next -> room.setHostId(next.getPlayerId()));
            }
            rooms.save(room);
            broadcast(room);
        });
    }

    /**
     * 방장이 자리 비움(연속 시간 초과)인 참가자를 강퇴한다 (PRD 4.3, FR-GAME-07).
     * 게임에서는 탈락 처리되고 방에서도 빠진다. 강퇴된 사람의 화면은 멤버 목록에서 자신이 빠진 것을 보고 처음 화면으로 간다.
     */
    public void kick(String roomId, String hostId, String targetId) {
        Room room = requireRoom(roomId);
        if (!room.hostedBy(hostId)) {
            throw ApiException.forbidden("NOT_HOST", "방장만 강퇴할 수 있습니다");
        }
        if (targetId == null || targetId.equals(hostId)) {
            throw ApiException.badRequest("INVALID_TARGET", "자기 자신은 강퇴할 수 없습니다");
        }
        requireMember(room, targetId);
        if (room.getStatus() != RoomStatus.IN_GAME || room.getGameId() == null) {
            throw ApiException.conflict("NOT_IN_GAME", "게임 중 자리 비움인 참가자만 강퇴할 수 있습니다");
        }
        // 방 락을 잡기 전에 게임을 처리한다. 강퇴로 게임이 끝나면 onGameFinished가 방 락을 잡는데,
        // 행동 처리(게임 락 → 방 락)와 반대 순서로 락을 잡으면 교착될 수 있다
        if (!games.kick(room.getGameId(), hostId, targetId)) {
            return; // 거절 사유는 GameService가 방장에게 보냈다
        }
        withLock(roomId, () -> rooms.find(roomId).ifPresent(fresh -> {
            fresh.getMembers().removeIf(m -> m.getPlayerId().equals(targetId));
            rooms.save(fresh);
            broadcast(fresh);
        }));
    }

    /** 게임이 끝나면 같은 멤버로 대기실에 돌아온다 (FR-ROOM-06) */
    @EventListener
    public void onGameFinished(GameFinishedEvent event) {
        if (event.roomId() == null) {
            return;
        }
        withLock(event.roomId(), () -> rooms.find(event.roomId()).ifPresent(room -> {
            room.setStatus(RoomStatus.LOBBY);
            room.getMembers().forEach(m -> m.setReady(false));
            rooms.save(room);
            broadcast(room);
        }));
    }

    // ------------------------------------------------------------------

    private Room update(String roomId, Consumer<Room> change) {
        Room[] result = new Room[1];
        withLock(roomId, () -> {
            Room room = requireRoom(roomId);
            change.accept(room);
            rooms.save(room);
            broadcast(room);
            result[0] = room;
        });
        return result[0];
    }

    private void withLock(String roomId, Runnable action) {
        ReentrantLock lock = locks.computeIfAbsent(roomId, k -> new ReentrantLock());
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    private void broadcast(Room room) {
        // Map 을 그대로 넘기면 convertAndSend(payload, headers) 오버로드와 모호해지므로 Object 로 고정한다
        Object message = Map.of("type", "ROOM_UPDATED", "room", room);
        messaging.convertAndSend("/topic/rooms/" + room.getRoomId(), message);
    }

    private Room byInvite(String inviteCode) {
        String code = inviteCode == null ? "" : inviteCode.strip().toUpperCase();
        return rooms.findByInvite(code)
                .orElseThrow(() -> ApiException.notFound("ROOM_NOT_FOUND", "방을 찾을 수 없습니다"));
    }

    private Room requireRoom(String roomId) {
        return rooms.find(roomId).orElseThrow(() -> ApiException.notFound("ROOM_NOT_FOUND", "방을 찾을 수 없습니다"));
    }

    private static RoomMember requireMember(Room room, String playerId) {
        RoomMember member = room.member(playerId);
        if (member == null) {
            throw ApiException.forbidden("NOT_MEMBER", "이 방의 참가자가 아닙니다");
        }
        return member;
    }

    private static void requireLobby(Room room) {
        if (room.getStatus() != RoomStatus.LOBBY) {
            throw ApiException.conflict("GAME_IN_PROGRESS", "게임이 진행 중입니다");
        }
    }

    private String newInviteCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            if (!rooms.inviteTaken(sb.toString())) {
                return sb.toString();
            }
        }
        throw new IllegalStateException("초대 코드를 만들지 못했습니다");
    }
}
