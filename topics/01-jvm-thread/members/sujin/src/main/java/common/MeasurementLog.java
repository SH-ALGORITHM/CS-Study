package common;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 측정 결과를 본인 폴더의 measurements.md 파일에 자동 누적 기록.
 *
 * <h3>자동 위치 감지</h3>
 * IntelliJ Working Directory 설정이 어떻든 상관없이, 이 클래스의 실제 위치에서
 * 위로 올라가며 build.gradle 가진 폴더를 찾아 그 안에 measurements.md를 만든다.
 *
 * <ul>
 *   <li>example 모듈에서 실행 → example/measurements.md</li>
 *   <li>chanhyeok 모듈에서 실행 → chanhyeok/measurements.md</li>
 * </ul>
 *
 * <h3>사용법</h3>
 * <pre>
 * common.MeasurementLog.save("s3", "synchronized", 0, 600.0);
 * common.MeasurementLog.save("s3", "AtomicInteger", 0, 18.0);
 * </pre>
 */
public class MeasurementLog {

    private static final Path FILE = resolveFile();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /**
     * 측정 결과 한 줄 기록.
     */
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
     * 이 클래스 파일의 위치에서 위로 올라가며 build.gradle 가진 폴더를 찾는다.
     * 없으면 현재 작업 디렉토리 fallback.
     */
    private static Path resolveFile() {
        try {
            var domain = MeasurementLog.class.getProtectionDomain();
            if (domain == null) return Path.of("measurements.md");
            var codeSource = domain.getCodeSource();
            if (codeSource == null) return Path.of("measurements.md");
            URL location = codeSource.getLocation();
            if (location == null) return Path.of("measurements.md");

            Path start = Path.of(location.toURI());
            // JAR 파일이면 그 부모로
            if (Files.isRegularFile(start)) {
                start = start.getParent();
            }

            // 최대 10단계 위로 올라가며 build.gradle 검색
            Path current = start;
            for (int i = 0; i < 10 && current != null; i++) {
                if (Files.exists(current.resolve("build.gradle"))
                    || Files.exists(current.resolve("build.gradle.kts"))) {
                    return current.resolve("measurements.md");
                }
                current = current.getParent();
            }
        } catch (Exception ignored) {
            // fallback
        }
        return Path.of("measurements.md");
    }
}
