package vn.drwp.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * ADR 015: bảng mang tiền tố theo module; cấm JOIN/FK xuyên module. Lint trên migration Flyway (classpath)
 * và trên SQL trong mã nguồn module.
 */
class TableOwnershipTest {

    static final Map<String, String> PREFIX = Map.of(
            "identity", "idn_", "metadata", "meta_", "record", "rec_", "workflow", "wf_",
            "file", "file_", "notification", "noti_", "audit", "aud_");
    static final Pattern MODULE_TABLE = Pattern.compile("\\b(idn|meta|rec|wf|file|noti|aud)_[a-z_]+\\b");
    static final Pattern CREATE_TABLE = Pattern.compile("(?i)CREATE\\s+TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?([a-z_]+)");

    @Test
    void migrationsCreateOnlyOwnedTables() throws IOException {
        List<String> violations = new ArrayList<>();
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:db/tenant/*/*.sql");
        assertThat(files).isNotEmpty();
        for (Resource r : files) {
            String path = r.getURL().toString();
            String module = path.replaceAll(".*/db/tenant/([a-z]+)/[^/]+$", "$1");
            String sql = new String(r.getContentAsByteArray(), StandardCharsets.UTF_8);
            Matcher m = CREATE_TABLE.matcher(sql);
            while (m.find()) {
                String table = m.group(2);
                boolean ok = "platform".equals(module)
                        ? table.matches("event_publication|outbox_event|inbox_event")
                        : table.startsWith(PREFIX.get(module));
                if (!ok) {
                    violations.add(module + " creates " + table + " (" + path + ")");
                }
            }
            Matcher refs = MODULE_TABLE.matcher(sql);
            while (refs.find()) {
                String prefix = refs.group(1) + "_";
                if (!"platform".equals(module) && !prefix.equals(PREFIX.get(module)) && !refs.group().startsWith("file_metadata_record")) {
                    violations.add(module + " migration references foreign table " + refs.group());
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void moduleSqlTouchesOnlyOwnTables() throws IOException {
        Path modulesDir = Path.of("..", "modules").toAbsolutePath().normalize();
        if (!Files.isDirectory(modulesDir)) {
            return; // chạy ngoài reactor
        }
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, String> e : PREFIX.entrySet()) {
            Path src = modulesDir.resolve(e.getKey()).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    Matcher m = MODULE_TABLE.matcher(Files.readString(f));
                    while (m.find()) {
                        if (!m.group().startsWith(e.getValue())) {
                            violations.add(e.getKey() + ": " + f.getFileName() + " references " + m.group());
                        }
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
    }
}
