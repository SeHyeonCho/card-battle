package com.cardbattle.server.room;

import com.cardbattle.server.session.SessionService;
import com.cardbattle.server.session.StompAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/rooms                      방 만들기 (X-Session-Token, X-Access-Code)
 * GET  /api/rooms/{inviteCode}         방 요약
 * POST /api/rooms/{inviteCode}/join    참가 (X-Session-Token)
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService rooms;
    private final SessionService sessions;

    public RoomController(RoomService rooms, SessionService sessions) {
        this.rooms = rooms;
        this.sessions = sessions;
    }

    @PostMapping
    public Room create(@RequestHeader(value = StompAuthInterceptor.TOKEN_HEADER, required = false) String token,
                       @RequestHeader(value = "X-Access-Code", required = false) String accessCode,
                       @RequestBody RoomSettings.Request settings,
                       HttpServletRequest http) {
        return rooms.create(sessions.require(token), settings, accessCode, http.getRemoteAddr());
    }

    @GetMapping("/{inviteCode}")
    public RoomService.RoomSummary summary(@PathVariable String inviteCode) {
        return rooms.summary(inviteCode);
    }

    @PostMapping("/{inviteCode}/join")
    public Room join(@RequestHeader(value = StompAuthInterceptor.TOKEN_HEADER, required = false) String token,
                     @PathVariable String inviteCode) {
        return rooms.join(sessions.require(token), inviteCode);
    }
}
