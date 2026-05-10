import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainRace {
    public static void main(String[] args) throws InterruptedException {

        AttendanceNone attendance = new AttendanceNone();

        ExecutorService executor = Executors.newFixedThreadPool(200);
        AtomicInteger successCount = new AtomicInteger(0);

        for(int i = 0; i < 50000; i++) {
            executor.submit(() -> {
                if (attendance.checkIn()) {
                    successCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("기대: 1 / 실제: " + successCount.get());

        MeasurementLog.save("s1", "race-repro", successCount.get() - 1, 0.0);
    }
}
