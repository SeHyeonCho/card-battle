package com.cardbattle.server.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/config  화면이 시작할 때 알아야 하는 서버 설정.
 * accessCodeRequired: 방을 만들 때 접근 코드가 필요한지 (필요할 때만 첫 화면에 입력칸을 보여 준다)
 */
@RestController
public class ClientConfigController {

    public record ClientConfig(boolean accessCodeRequired) {
    }

    private final AccessGuard guard;

    public ClientConfigController(AccessGuard guard) {
        this.guard = guard;
    }

    @GetMapping("/api/config")
    public ClientConfig config() {
        return new ClientConfig(guard.accessCodeRequired());
    }
}
