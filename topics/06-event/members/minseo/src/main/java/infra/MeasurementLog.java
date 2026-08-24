package infra;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 측정 결과 println 헬퍼 + 자동 파일 저장 기능 추가 */
public class MeasurementLog {

    private static final Path FILE = resolveFile();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public static void title(String s) {
        System.out.println();
        System.out.println("=== " + s + " ===");
        append("- [" + LocalDateTime.now().format(TIME) + "] " + s + "\n");
    }

    public static void row(String label, Object value) {
        String line = String.format("  %-40s %s", label, value);
        System.out.println(line);
        append("  - " + label + ": " + value + "\n");
    }

    public static void section(String s) {
        System.out.println();
        System.out.println("--- " + s + " ---");
        append("  - **" + s + "**\n");
    }

    public static void save(String stage, String note) {
        String line = String.format("- [%s] %s · %s%n",
            LocalDateTime.now().format(TIME), stage, note);
        System.out.print(line);
        append(line);
    }

    private static void append(String content) {
        try {
            if (!Files.exists(FILE)) {
                Files.writeString(FILE, "# 측정 및 관찰 기록\n\n", StandardOpenOption.CREATE);
            }
            Files.writeString(FILE, content, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("⚠️ measurements.md 기록 실패: " + e.getMessage());
        }
    }

    public static String thread() {
        return Thread.currentThread().getName();
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
            if (Files.isRegularFile(start)) start = start.getParent();

            Path current = start;
            for (int i = 0; i < 10 && current != null; i++) {
                if (Files.exists(current.resolve("build.gradle"))) {
                    return current.resolve("measurements.md");
                }
                current = current.getParent();
            }
        } catch (Exception ignored) {}
        return Path.of("measurements.md");
    }
}
