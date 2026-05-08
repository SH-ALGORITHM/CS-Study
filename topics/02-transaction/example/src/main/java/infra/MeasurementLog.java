package infra;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 측정 결과를 본인 폴더의 measurements.md 파일에 자동 누적 기록.
 * (1주차 example 과 동일 — 패키지 위치만 infra 로)
 *
 * <h3>자동 위치 감지</h3>
 * IntelliJ Working Directory 설정과 무관하게, 이 클래스의 실제 위치에서
 * 위로 올라가며 build.gradle 가진 폴더를 찾아 그 안에 measurements.md 를 만든다.
 *
 * <ul>
 *   <li>example 모듈에서 실행 → example/measurements.md</li>
 *   <li>chanhyeok 모듈에서 실행 → chanhyeok/measurements.md</li>
 * </ul>
 *
 * <h3>사용법</h3>
 * <pre>
 * MeasurementLog.save("s3", "READ_COMMITTED", lostUpdates, millis);
 * </pre>
 */
public final class MeasurementLog {

    private static final Path FILE = resolveFile();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private MeasurementLog() {}

    public static void save(String stage, String method, double misses, double millis) {
        try {
            if (!Files.exists(FILE)) {
                Files.writeString(FILE,
                    "# 측정 기록\n\n자동 누적. 옆에 해석 메모는 직접 추가하세요.\n\n");
            }

            String line = String.format("- [%s] %s · %s: 누락 %.1f / %.1fms%n",
                LocalDateTime.now().format(TIME), stage, method, misses, millis);
            Files.writeString(FILE, line, StandardOpenOption.APPEND);

            System.out.println("→ " + FILE.toAbsolutePath() + " 에 기록됨");
        } catch (IOException e) {
            System.err.println("⚠️ measurements.md 기록 실패: " + e.getMessage());
        }
    }

    /**
     * 실패 횟수까지 함께 기록 (2주차 STAGE 2~4 용).
     * <p>출력 형식: {@code 누락 X / 실패 Y / Z ms}
     *
     * @param stage   "s2-1", "s3", "s4" 등
     * @param method  격리 수준 또는 시나리오 이름
     * @param misses  Lost Update 발생 횟수 (또는 s4 에선 0)
     * @param failed  트랜잭션 실패 횟수 (40001, 40P01 등)
     * @param millis  응답시간 (ms)
     */
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
