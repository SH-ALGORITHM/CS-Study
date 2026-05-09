import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * runOnce()
 * = 한 번 실험 (1000+ 스레드 동시 환불 시도)
 *
 * measure()
 * = runOnce()를 RUNS번 반복해서 평균 계산
 *
 * Result
 * = 평균 결과
 *
 * RunResult
 * = 1회 실행 결과
 *
 * 변종 4종(NoSync / Sync / Atomic / Volatile)을 동일 코드로 측정하기 위해
 * Refundable 인터페이스 + Supplier<Refundable> factory 패턴 사용.
 */
public class Stage3Runner {

    private static final int THREADS  = 200;       // 동시 스레드 수
    private static final int ATTEMPTS = 10_000;    // 출금 시도 횟수
    private static final int RUNS     = 5;         // 측정 반복 (평균 내기)
    private static final int WARMUP   = 5000;      // JIT 워밍업 횟수 (변종별)

    public static void main(String[] args) throws Exception {

        System.out.println("[워밍업] 변종별 " + WARMUP + "회 실행 중...");
        warmup(RefundService::new);
        warmup(RefundServiceSync::new);
        warmup(RefundServiceAtomic::new);
        warmup(RefundServiceVolatile::new);
        System.out.println("[워밍업] 완료\n");

        System.out.println("=== Stage3 해결책 비교 ===");
        System.out.println("(" + THREADS + "스레드 × " + ATTEMPTS + "요청 × " + RUNS + "회 평균)\n");

        Result none   = measure(RefundService::new);
        Result sync   = measure(RefundServiceSync::new);
        Result atomic = measure(RefundServiceAtomic::new);
        Result vol    = measure(RefundServiceVolatile::new);

        System.out.println("| 방식 | 초과 환불 평균 | 평균 응답 시간(ms) |");
        System.out.println("|---|---:|---:|");
        System.out.printf("| 해결책 없음   | %.1f | %.1f |%n", none.avgMisses,   none.avgMillis);
        System.out.printf("| synchronized  | %.1f | %.1f |%n", sync.avgMisses,   sync.avgMillis);
        System.out.printf("| AtomicBoolean | %.1f | %.1f |%n", atomic.avgMisses, atomic.avgMillis);
        System.out.printf("| volatile      | %.1f | %.1f |%n", vol.avgMisses,    vol.avgMillis);
        System.out.println();

        MeasurementLog.save("s3", "해결책 없음",    none.avgMisses,   none.avgMillis);
        MeasurementLog.save("s3", "synchronized",  sync.avgMisses,   sync.avgMillis);
        MeasurementLog.save("s3", "AtomicBoolean", atomic.avgMisses, atomic.avgMillis);
        MeasurementLog.save("s3", "volatile",      vol.avgMisses,    vol.avgMillis);
    }

    private static void warmup(Supplier<Refundable> factory) throws InterruptedException {
        for (int i = 0; i < WARMUP; i++) {
            factory.get().refund();
        }
    }

    private static Result measure(Supplier<Refundable> factory) throws Exception {
        double totalMisses = 0;
        double totalMillis = 0;

        for (int run = 0; run < RUNS; run++) {
            Refundable refundService = factory.get();   // 매 회차 새 인스턴스
            RunResult result = runOnce(refundService);
            totalMisses += result.excessRefunds;
            totalMillis += result.elapsedMillis;
        }

        return new Result(totalMisses / RUNS, totalMillis / RUNS);
    }

    private static RunResult runOnce(Refundable refundService) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startSignal = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger(0);

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                    if (refundService.refund()) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        Thread.sleep(50);

        long start = System.nanoTime();
        startSignal.countDown();

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        long elapsed = System.nanoTime() - start;

        int excessRefunds = Math.max(0, success.get() - 1);
        double elapsedMillis = elapsed / 1_000_000.0;

        return new RunResult(excessRefunds, elapsedMillis);
    }

    static class Result {
        final double avgMisses;
        final double avgMillis;

        Result(double avgMisses, double avgMillis) {
            this.avgMisses = avgMisses;
            this.avgMillis = avgMillis;
        }
    }

    static class RunResult {
        final int excessRefunds;
        final double elapsedMillis;

        RunResult(int excessRefunds, double elapsedMillis) {
            this.excessRefunds = excessRefunds;
            this.elapsedMillis = elapsedMillis;
        }
    }
}
