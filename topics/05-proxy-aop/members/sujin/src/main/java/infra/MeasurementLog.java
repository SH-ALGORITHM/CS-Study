package infra;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MeasurementLog {

    private static final Path FILE = resolveFile();
    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** STAGE 1·2·4 — 관찰 한 줄 (자유 텍스트). */
    public static void save(String stage, String note) {
        append(String.format("- [%s] %s · %s%n",
            LocalDateTime.now().format(TIME), stage, note));
    }

    /** STAGE 3 — 정량 측정 (숫자 + 단위). */
    public static void save(String stage, String label, double value, String unit)
    {
        append(String.format("- [%s] %s · %s: %.1f%s%n",
            LocalDateTime.now().format(TIME), stage, label, value, unit));
    }

    private static void append(String line) {
        try {
            if (!Files.exists(FILE)) {
                Files.writeString(FILE,
                    "# 측정 기록\n\n자동 누적. 옆에 해석 메모는 직접 추가하세요.\n\n");
            }
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
            if (Files.isRegularFile(start)) start = start.getParent();   // JAR이면 부모로

            Path current = start;
            for (int i = 0; i < 10 && current != null; i++) {            // 위로최대 10단계
                if (Files.exists(current.resolve("build.gradle"))
                    || Files.exists(current.resolve("build.gradle.kts"))) {
                    return current.resolve("measurements.md");
                }
                current = current.getParent();
            }
        } catch (Exception ignored) {
        }
        return Path.of("measurements.md");
    }

    // 콘솔 구분용
    public static void title(String s) {
        System.out.println();
        System.out.println("=== " + s + " ===");
    }
}
