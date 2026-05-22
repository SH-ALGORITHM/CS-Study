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
 * (1~3 주차 example 과 동일 패턴)
 *
 * <h3>자동 위치 감지</h3>
 * IntelliJ Working Directory 설정과 무관하게, 이 클래스의 실제 위치에서
 * 위로 올라가며 build.gradle 가진 폴더를 찾아 그 안에 measurements.md 를 만든다.
 *
 * <h3>4 주차 측정 시그니처 — 단일 범용 형식</h3>
 * <ul>
 *   <li>{@code save(stage, method, result)} — 모든 측정에 동일 형식 사용</li>
 *   <li>예: {@code save("s3-1", "순수 main()", "부팅 시간 5.2ms")}</li>
 *   <li>예: {@code save("s3-3", "프로토타입 1000회 호출", "생성자 호출 1000")}</li>
 *   <li>예: {@code save("s3-4", "@Lazy 적용 후", "부팅 시간 240ms (적용 전 2240ms)")}</li>
 * </ul>
 *
 * 측정 항목이 동시성처럼 정형화되지 않아 (부팅 시간 / Bean 수 / 카운트 / @Lazy 효과 등)
 * 호출부에서 단위까지 포함한 result 문자열을 직접 만든다.
 */
public final class MeasurementLog {

    private static final Path FILE = resolveFile();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private MeasurementLog() {}

    public static void save(String stage, String method, String result) {
        try {
            if (!Files.exists(FILE)) {
                Files.writeString(FILE,
                    "# 측정 기록\n\n자동 누적. 옆에 해석 메모는 직접 추가하세요.\n\n");
            }

            String line = String.format("- [%s] %s · %s: %s%n",
                LocalDateTime.now().format(TIME), stage, method, result);
            Files.writeString(FILE, line, StandardOpenOption.APPEND);

            System.out.println("→ " + FILE.toAbsolutePath() + " 에 기록됨");
        } catch (IOException e) {
            System.err.println("measurements.md 기록 실패: " + e.getMessage());
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
