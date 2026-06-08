package infra;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 측정 / 관찰 결과를 본인 폴더의 measurements.md 에 자동 누적 기록.
 * (1~3 주차 example 과 동일한 자동 위치 감지 패턴)
 *
 * <h3>자동 위치 감지</h3>
 * IntelliJ Working Directory 설정과 무관하게, 이 클래스의 실제 위치에서
 * 위로 올라가며 build.gradle 가진 폴더를 찾아 그 안에 measurements.md 를 만든다.
 *
 * <h3>6 주차(Spring Event) 형식</h3>
 * 동시성 측정(누락/실패/ms)이 아니라 자유 텍스트 관찰이 많으므로 {@code save(stage, note)} 사용.
 * <pre>
 *   MeasurementLog.save("s1", "HelloEvent 발행 → 동기 리스너 호출 순서 확인 (thread=main)");
 *   // → measurements.md 에 "- [06-08 14:00] s1 · HelloEvent 발행 → ..." 한 줄 append
 * </pre>
 * 시간 측정이 필요한 STAGE 3(@Async) 에선 {@code save(stage, note, millis)} 로 ms 도 기록.
 */
public final class MeasurementLog {

    private static final Path FILE = resolveFile();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private MeasurementLog() {}

    /** 자유 텍스트 관찰 (STAGE 1~2 주로 사용). */
    public static void save(String stage, String note) {
        String line = String.format("- [%s] %s · %s%n",
            LocalDateTime.now().format(TIME), stage, note);
        append(line);
    }

    /** 시간 측정 포함 (STAGE 3 @Async — publisher 블록 시간 등). */
    public static void save(String stage, String note, double millis) {
        String line = String.format("- [%s] %s · %s (%.1fms)%n",
            LocalDateTime.now().format(TIME), stage, note, millis);
        append(line);
    }

    private static void append(String line) {
        try {
            if (!Files.exists(FILE)) {
                Files.writeString(FILE,
                    "# 측정 기록 (6주차 — Spring Event)\n\n자동 누적. 옆에 해석 메모는 직접 추가하세요.\n\n");
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
