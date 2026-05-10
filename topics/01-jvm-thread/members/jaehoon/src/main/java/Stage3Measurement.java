import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Stage3Measurement {

    private static final int THREADS  = 200;
    private static final int ATTEMPTS = 1000;
    private static final int RUNS     = 5;
    private static final int WARMUP   = 5000;

    // === 인터페이스로 분리할 수 있게 ===
    interface Withdraw {
        void like();
        int getCount();
    }

    interface CounterFactory {
        Withdraw create();
    }

    // === 1. race  ===
    static class SimpleLikeCounter implements Withdraw {
        private int count = 0;
        public void like() {
            count++;
        }

        public int getCount() {
            return count;
        }
    }

    // === 2. synchronized ===
    static class SyncLikeCounter implements Withdraw {
        private int count = 0;
        public synchronized void like() {
            count++;
        }

        public int getCount() {
            return count;
        }
    }

    // === 3. atomicInteger ===
    static class AtomicLikeCounter implements Withdraw {
        private final AtomicInteger count = new AtomicInteger(0);
        public void like() {
            count.incrementAndGet();
        }
        public int getCount() {
            return count.get();
        }
    }

    // === 측정 결과 ===
    static class Result {
        final double avgMisses, avgMs;
        Result(double m, double t) {
            this.avgMisses = m;
            this.avgMs = t;
        }
    }

    // === s1복붙  ===
    static Result measure(CounterFactory factory) throws Exception {
        double totalMisses = 0;
        double totalMs = 0;

        for (int run = 0; run < RUNS; run++) {
            Withdraw counter = factory.create();
            ExecutorService executor = Executors.newFixedThreadPool(THREADS);
            CountDownLatch startSignal = new CountDownLatch(1);

            for (int i = 0; i < ATTEMPTS; i++) {
                executor.submit(() -> {
                    try {
                        startSignal.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    counter.like();
                });
            }

            Thread.sleep(50);

            long start = System.nanoTime();
            startSignal.countDown();

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            double ms = (System.nanoTime() - start) / 1_000_000.0;
            int misses = ATTEMPTS - counter.getCount();

            totalMisses += misses;
            totalMs += ms;
        }

        return new Result(totalMisses / RUNS, totalMs / RUNS);
    }

    // 예시코드 참고
    public static void main(String[] args) throws Exception {

        // 워밍업
        System.out.println("[워밍업] " + WARMUP + "회 실행 중...");
        for (int i = 0; i < WARMUP; i++) {
            SimpleLikeCounter w = new SimpleLikeCounter();
            w.like();
        }
        System.out.println("[워밍업] 완료\n");

        // 측정
        Result none = measure(SimpleLikeCounter::new);
        Result sync = measure(SyncLikeCounter::new);
        Result atom = measure(AtomicLikeCounter::new);

        // 결과 출력
        System.out.println("=== S3 측정 결과 (" + THREADS + "스레드 × " + ATTEMPTS + "회 × " + RUNS + "회 평균) ===\n");
        System.out.println("| 방식           | 누락 (평균) | 응답 (ms) |");
        System.out.println("|---------------|-------------|-----------|");
        System.out.printf("| %-13s | %11.1f | %9.1f |%n", "해결책 없음", none.avgMisses, none.avgMs);
        System.out.printf("| %-13s | %11.1f | %9.1f |%n", "synchronized", sync.avgMisses, sync.avgMs);
        System.out.printf("| %-13s | %11.1f | %9.1f |%n", "AtomicInteger", atom.avgMisses, atom.avgMs);

        // 자동 기록
        System.out.println();
        MeasurementLog.save("s3", "해결책 없음", none.avgMisses, none.avgMs);
        MeasurementLog.save("s3", "synchronized", sync.avgMisses, sync.avgMs);
        MeasurementLog.save("s3", "AtomicInteger", atom.avgMisses, atom.avgMs);
    }
}
