package com.cardbattle.server.pack;

import com.cardbattle.engine.pack.PackFormatException;
import com.cardbattle.server.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 서버 시작 시 app.packs.auto-import-dirs 의 카드팩 폴더를 자동으로 DB에 넣는다.
 * 원작 카드팩은 로컬 전용 packs/original(gitignore)에 두며, 폴더가 없으면 경고만 남긴다.
 */
@Component
public class PackBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PackBootstrap.class);

    private final AppProperties props;
    private final PackFiles files;
    private final PackImportService importer;
    private final PackCatalog catalog;

    public PackBootstrap(AppProperties props, PackFiles files, PackImportService importer, PackCatalog catalog) {
        this.props = props;
        this.files = files;
        this.importer = importer;
        this.catalog = catalog;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> dirs = props.packs() == null || props.packs().autoImportDirs() == null
                ? List.of()
                : props.packs().autoImportDirs();
        for (String d : dirs) {
            if (d == null || d.isBlank()) {
                continue;
            }
            Path dir = Path.of(d).toAbsolutePath().normalize();
            if (!Files.isDirectory(dir)) {
                log.warn("카드팩 폴더가 없습니다: {}", dir);
                continue;
            }
            try {
                PackFiles.RawPack raw = files.read(dir);
                PackImportService.Result result = importer.importPack(raw.meta(), raw.cards());
                log.info("카드팩 {} → {}", dir.getFileName(), result);
            } catch (PackFormatException e) {
                log.error("카드팩 {} 을(를) 불러오지 못했습니다\n{}", dir, e.getMessage());
            } catch (IOException | RuntimeException e) {
                // JSON 문법 오류(JacksonException)도 여기로 온다. 서버는 계속 뜨게 둔다
                log.error("카드팩 {} 을(를) 읽지 못했습니다", dir, e);
            }
        }
        catalog.reload();
        log.info("사용 가능한 카드팩: {}", catalog.list(true));
    }
}
