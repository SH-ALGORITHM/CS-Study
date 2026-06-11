package infra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 측정 결과 println 헬퍼. 4, 5 주차 패턴 그대로. */
public class MeasurementLog {

    private static final Path MEASUREMENTS = Path.of("measurements.md");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public static void title(String s) {
        System.out.println();
        System.out.println("=== " + s + " ===");
    }

    public static void row(String label, Object value) {
        System.out.printf("  %-40s %s%n", label, value);
    }

    public static void section(String s) {
        System.out.println();
        System.out.println("--- " + s + " ---");
    }

    public static String thread() {
        return Thread.currentThread().getName();
    }

    public static void record(String stage, String result) {
        String line = "- [" + LocalDateTime.now().format(TIMESTAMP) + "] "
            + stage + " · " + result + System.lineSeparator();

        try {
            initializeMeasurementsFile();
            Files.writeString(
                MEASUREMENTS,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND
            );
            System.out.println("  [측정 기록] " + MEASUREMENTS.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("measurements.md 기록 실패", e);
        }
    }

    private static void initializeMeasurementsFile() throws IOException {
        if (Files.notExists(MEASUREMENTS)) {
            Files.writeString(
                MEASUREMENTS,
                "# 측정 기록" + System.lineSeparator()
                    + System.lineSeparator()
                    + "실행 시 자동 누적됩니다." + System.lineSeparator()
                    + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW
            );
        }
    }
}
