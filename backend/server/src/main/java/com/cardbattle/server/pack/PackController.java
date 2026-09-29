package com.cardbattle.server.pack;

import com.cardbattle.server.common.AccessGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * GET  /api/packs         선택 가능한 카드팩 (접근 코드가 맞을 때만 PRIVATE 포함)
 * POST /api/admin/packs   카드팩 import (X-Admin-Key 필요)
 */
@RestController
@RequestMapping("/api")
public class PackController {

    public record PackSummary(String code, String name, int version, String visibility) {
    }

    public record ImportRequest(Map<String, Object> pack, List<Map<String, Object>> cards) {
    }

    private final PackCatalog catalog;
    private final PackImportService importer;
    private final AccessGuard guard;

    public PackController(PackCatalog catalog, PackImportService importer, AccessGuard guard) {
        this.catalog = catalog;
        this.importer = importer;
        this.guard = guard;
    }

    @GetMapping("/packs")
    public List<PackSummary> list(@RequestHeader(value = "X-Access-Code", required = false) String accessCode) {
        return catalog.list(guard.validAccessCode(accessCode)).stream()
                .map(row -> new PackSummary(row.code(), row.name(), row.version(), row.visibility()))
                .toList();
    }

    @PostMapping("/admin/packs")
    public Map<String, Object> importPack(@RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
                                          @RequestBody ImportRequest request) {
        guard.requireAdminKey(adminKey);
        PackImportService.Result result = importer.importPack(request.pack(), request.cards());
        catalog.reload();
        return Map.of("result", result.name());
    }
}
