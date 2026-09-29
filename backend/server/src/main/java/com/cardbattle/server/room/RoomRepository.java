package com.cardbattle.server.room;

import com.cardbattle.server.common.Json;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/** 방을 Redis에 저장한다. 12시간 동안 아무 변화가 없으면 사라진다. */
@Repository
public class RoomRepository {

    private static final Duration TTL = Duration.ofHours(12);

    private final StringRedisTemplate redis;
    private final Json json;

    public RoomRepository(StringRedisTemplate redis, Json json) {
        this.redis = redis;
        this.json = json;
    }

    public void save(Room room) {
        redis.opsForValue().set("room:" + room.getRoomId(), json.write(room), TTL);
        redis.opsForValue().set("invite:" + room.getInviteCode(), room.getRoomId(), TTL);
    }

    public Optional<Room> find(String roomId) {
        String value = redis.opsForValue().get("room:" + roomId);
        return value == null ? Optional.empty() : Optional.of(json.read(value, Room.class));
    }

    public Optional<Room> findByInvite(String inviteCode) {
        String roomId = redis.opsForValue().get("invite:" + inviteCode);
        return roomId == null ? Optional.empty() : find(roomId);
    }

    public boolean inviteTaken(String inviteCode) {
        return Boolean.TRUE.equals(redis.hasKey("invite:" + inviteCode));
    }

    public void delete(Room room) {
        redis.delete("room:" + room.getRoomId());
        redis.delete("invite:" + room.getInviteCode());
    }
}
