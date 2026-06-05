package infra;

/** 측정 / 출력 헬퍼. 5, 6 주차 패턴 그대로. */
public class MeasurementLog {

    public static void title(String s) {
        System.out.println();
        System.out.println("=== " + s + " ===");
    }

    public static void section(String s) {
        System.out.println();
        System.out.println("--- " + s + " ---");
    }

    public static void row(String label, Object value) {
        System.out.printf("  %-40s %s%n", label, value);
    }

    public static String thread() {
        return Thread.currentThread().getName();
    }

    /** SQL 로그 사이에 명확히 구분되는 마커 출력 */
    public static void marker(String s) {
        System.out.println(">>>>> " + s);
    }
}
