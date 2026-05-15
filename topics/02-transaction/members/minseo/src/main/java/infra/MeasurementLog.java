package infra;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class MeasurementLog {

    private static final Path FILE = resolveFile();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private MeasurementLog() {}

    public static void save(String stage, String method, double misses, double failed, double millis) {
        try {
            if (!Files.exists(FILE)) {
                Files.writeString(FILE,
                    "# 측정 기록\n\n자동 누적. 옆에 해석 메모는 직접 추가하세요.\n\n");
            }

            String line = String.format("- [%s] %s · %s: 누락 %.1f / 실패 %.1f / %.1fms%n",
                LocalDateTime.now().format(TIME), stage, method, misses, failed, millis);
            Files.writeString(FILE, line, StandardOpenOption.APPEND);

            System.out.println("→ " + FILE.toAbsolutePath() + " 에 기록됨");
        } catch (IOException e) {
            System.err.println("⚠️ measurements.md 기록 실패: " + e.getMessage());
        }
    }

    private static Path resolveFile() {
        try {
            var domain = MeasurementLog.class.getProtectionDomain();
            if (domain == null) return Path.of("measurements.md");
            var codeSource = domain.getCodeSource();
            if (codeSource == null) return Path.of("measurements.md");
            URL location = codeSource.getLocation();
            if (location == null) return Path.of("measurements.md");

            Path start = Path.of(location.toURI());
            if (Files.isRegularFile(start)) {
                start = start.getParent();
            }

            Path current = start;
            for (int i = 0; i < 10 && current != null; i++) {
                if (Files.exists(current.resolve("build.gradle"))
                    || Files.exists(current.resolve("build.gradle.kts"))) {
                    return current.resolve("measurements.md");
                }
                current = current.getParent();
            }
        } catch (Exception ignored) {}
        return Path.of("measurements.md");
    }
}
