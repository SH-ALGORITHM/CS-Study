package infra;

/** 측정 결과 println 헬퍼. 4 주차 패턴 그대로. */
public class MeasurementLog {

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

    public static String classOf(Object obj) {
        return obj.getClass().getName();
    }
}
