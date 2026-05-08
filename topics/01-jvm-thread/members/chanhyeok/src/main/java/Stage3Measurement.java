import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Stage3Measurement {
    // 측정 파라미터
    static final int THREADS = 200;
    static final int ATTEMPTS = 1000;
    static final int RUNS = 5;
    static final int WARMUP = 5000;

    public static void main(String[] args) throws InterruptedException {
        for (int i = 0; i < WARMUP; i++) {
            SimpleCoupon warmUp = new SimpleCoupon();
            warmUp.claim(1);
        }

        for (int i = 0; i < WARMUP; i++) {
            SyncCoupon warmUp = new SyncCoupon();
            warmUp.claim(1);
        }

        for (int i = 0; i < WARMUP; i++) {
            AtomicCoupon warmUp = new AtomicCoupon();
            warmUp.claim(1);
        }

        Result none = measureMultipleRuns(SimpleCoupon::new);
        Result sync = measureMultipleRuns(SyncCoupon::new);
        Result atom = measureMultipleRuns(AtomicCoupon::new);

        // === Step 3. 결과 정리 출력 (측정 끝난 후에만) ===
        System.out.println("| 방식           | 누락 (평균) | 응답 (ms) |");
        System.out.println("|---------------|-------------|-----------|");
        System.out.printf ("| %-13s | %11.1f | %9.1f |%n", "해결책 없음",   none.avgMisses, none.avgMillis);
        System.out.printf ("| %-13s | %11.1f | %9.1f |%n", "synchronized", sync.avgMisses, sync.avgMillis);
        System.out.printf ("| %-13s | %11.1f | %9.1f |%n", "AtomicInteger", atom.avgMisses, atom.avgMillis);

        System.out.println("\n해석:");
        System.out.println("- 해결책 없음: 누락 발생 (race condition 그대로)");
        System.out.println("- synchronized: 누락 0이지만 줄 서서 기다리느라 느림");
        System.out.println("- AtomicInteger: 누락 0 + 빠름 (CAS 방식, 줄 안 섬)");

        // === Step 4. 자동 기록 — measurements.md 에 한 줄씩 누적 ===
        System.out.println();
        MeasurementLog.save("s3", "해결책 없음",   none.avgMisses, none.avgMillis);
        MeasurementLog.save("s3", "synchronized", sync.avgMisses, sync.avgMillis);
        MeasurementLog.save("s3", "AtomicInteger", atom.avgMisses, atom.avgMillis);
    }

    private static Result measureMultipleRuns(CouponFactory factory) throws InterruptedException {
        double totalMisses = 0;
        double totalMillis = 0;

        for (int run = 0; run < RUNS; run++) {
            Coupon coupon = factory.create();
            ExecutorService executor = Executors.newFixedThreadPool(THREADS);
            AtomicInteger success = new AtomicInteger(0);
            CountDownLatch startSignal = new CountDownLatch(1);

            for (int i = 0; i < ATTEMPTS; i++) {
                executor.submit(() -> {
                    try {
                        startSignal.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (coupon.claim(1)) {
                        success.incrementAndGet();
                    }
                });
            }

            Thread.sleep(100);

            long start = System.nanoTime();
            startSignal.countDown();

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            long end = System.nanoTime() - start;

            int misses = Math.abs(success.get() - 100);
            totalMisses += misses;
            totalMillis += end / 1_000_000.0;
        }

        return new Result(totalMisses / RUNS, totalMillis / RUNS);
    }


    interface Coupon {
        boolean claim(int amount);
    }

    interface CouponFactory {
        Coupon create();
    }

    static class Result {
        final double avgMisses, avgMillis;

        Result(double avgMisses, double avgMillis) {
            this.avgMisses = avgMisses;
            this.avgMillis = avgMillis;
        }
    }

    static class SimpleCoupon implements Coupon {
        private int availableCoupons = 100;

        @Override
        public boolean claim(int amount) {
            if (availableCoupons >= amount) {
                availableCoupons -= amount;
                return true;
            }
            return false;
        }
    }

    static class SyncCoupon implements Coupon {
        private int availableCoupons = 100;

        @Override
        public synchronized boolean claim(int amount) {
            if (availableCoupons >= amount) {
                availableCoupons -= amount;
                return true;
            }
            return false;
        }
    }

    static class AtomicCoupon implements Coupon {
        private final AtomicInteger availableCoupons = new AtomicInteger(100);

        @Override
        public boolean claim(int amount) {
            while (true) {
                int currentAvailableCoupons = availableCoupons.get();
                if (currentAvailableCoupons < amount) {
                    return false;
                }
                if (availableCoupons.compareAndSet(currentAvailableCoupons, currentAvailableCoupons - amount)) {
                    return true;
                }
            }
        }
    }

}
