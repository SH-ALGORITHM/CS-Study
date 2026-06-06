package infra;

public class MeasurementLog {
    public static void title(String s) {
        System.out.println();
        System.out.println("=== " + s + " ===");
    }
    public static void section(String s) {
        System.out.println();
        System.out.println("--- " + s + " ---");
    }
    public static void marker(String s) {
        System.out.println(">>>>> " + s);
    }
}
