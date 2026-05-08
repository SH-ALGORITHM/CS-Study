import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Stage4VirtualThread {

    public static void main(String[] args) throws InterruptedException {
        CouponEvent event = new CouponEvent(100);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch startSignal = new CountDownLatch(1);


        for (int i = 0; i < 10_000; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (event.claim(1)) {
                    successCount.incrementAndGet();
                }
            });
        }

        Thread.sleep(100);

        long start = System.nanoTime();
        startSignal.countDown();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long end = System.nanoTime() - start;

        // 6. 결과 출력 (측정 끝난 후에만)
        System.out.println("=== STAGE 4 — Virtual Threads ===");
        System.out.println("스레드: 10,000개 (가상)");
        System.out.println("기대: 성공 100개, 잔여 0개");
        System.out.println("실제: 성공 " + successCount.get() + "개, 잔여 " + event.getAvailableCoupons() + "개");
        System.out.printf ("시간: %.1fms%n", end / 1_000_000.0);
        System.out.println();

        // 7. 간단 해석
        if (successCount.get() > 100 || event.getAvailableCoupons() < 0) {
            System.out.println("→ ⚠️ Virtual Threads 환경에서도 race 발생!");
        }

        System.out.println();

        // 8. 자동 기록 — measurements.md 에 한 줄 누적
        System.out.println();
        long elapsedMs = end / 1_000_000;
        MeasurementLog.save("s4", "Virtual Threads (10000개)",
            Math.abs(successCount.get() - 100), elapsedMs);
    }
}
