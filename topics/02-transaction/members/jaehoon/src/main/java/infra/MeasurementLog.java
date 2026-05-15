package infra;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class MeasurementLog {

    private static Path file;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private MeasurementLog() {}

    /**
     * main() 시작할 때 호출 — 이 클래스의 위치 기준으로 measurements.md 경로 결정.
     * 1주차/2주차 모듈이 같은 classpath에 있을 때 올바른 폴더에 기록하기 위함.
     */
    public static void setAnchorClass(Class<?> anchor) {
        file = resolveFile(anchor);
    }

    public static void save(String stage, String method, double misses, double millis) {
        Path target = getFile();
        try {
            if (!Files.exists(target)) {
                Files.writeString(target,
                    "# 측정 기록\n\n자동 누적. 옆에 해석 메모는 직접 추가하세요.\n\n");
            }

            String line = String.format("- [%s] %s · %s: 누락 %.1f / %.1fms%n",
                LocalDateTime.now().format(TIME), stage, method, misses, millis);
            Files.writeString(target, line, StandardOpenOption.APPEND);

            System.out.println("→ " + target.toAbsolutePath() + " 에 기록됨");
        } catch (IOException e) {
            System.err.println("⚠️ measurements.md 기록 실패: " + e.getMessage());
        }
    }

    public static void save(String stage, String method, double misses, double failed, double millis) {
        Path target = getFile();
        try {
            if (!Files.exists(target)) {
                Files.writeString(target,
                    "# 측정 기록\n\n자동 누적. 옆에 해석 메모는 직접 추가하세요.\n\n");
            }

            String line = String.format("- [%s] %s · %s: 누락 %.1f / 실패 %.1f / %.1fms%n",
                LocalDateTime.now().format(TIME), stage, method, misses, failed, millis);
            Files.writeString(target, line, StandardOpenOption.APPEND);

            System.out.println("→ " + target.toAbsolutePath() + " 에 기록됨");
        } catch (IOException e) {
            System.err.println("⚠️ measurements.md 기록 실패: " + e.getMessage());
        }
    }

    private static Path getFile() {
        if (file == null) {
            file = resolveFile(MeasurementLog.class);
        }
        return file;
    }

    private static Path resolveFile(Class<?> anchor) {
        try {
            var domain = anchor.getProtectionDomain();
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
