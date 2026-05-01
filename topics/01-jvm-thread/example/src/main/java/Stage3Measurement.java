import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * STAGE 3 — 스레드 수 변화 측정 + 해결책 비교
 *
 * <h3>이 단계부터 금지 키워드 해제</h3>
 * synchronized, AtomicInteger 등 사용 가능. 단 30분 직접 시도 후 적용.
 *
 * <h3>측정 원칙</h3>
 * <ol>
 *   <li><b>JIT 워밍업</b>: 측정 전 5,000번 미리 실행 (결과 안 봄)<br>
 *       — JVM이 코드를 충분히 돌려야 최적화가 적용되어 측정이 안정됩니다</li>
 *   <li><b>여러 번 평균</b>: 같은 조건 5회 반복 평균<br>
 *       — 1회만 보면 GC, 스케줄링 영향이 큽니다</li>
 *   <li><b>측정 중간 출력 금지</b>: 작업 안에 println 절대 X<br>
 *       — 출력 한 줄에 동기화 효과가 있어 측정이 망가집니다</li>
 * </ol>
 *
 * <h3>예상 출력</h3>
 * <pre>
 *   | 방식           | 누락 (평균) | 응답 (ms) |
 *   |---------------|-------------|-----------|
 *   | 해결책 없음     |        18.4 |     50.2  |
 *   | synchronized  |         0.0 |    600.5  |  (12배 느림)
 *   | AtomicInteger |         0.0 |     18.3  |  (2.7배 빠름)
 * </pre>
 */
public class Stage3Measurement {

    // === 측정 파라미터 ===
    private static final int THREADS  = 200;      // 동시 스레드 수
    private static final int ATTEMPTS = 10_000;   // 출금 시도 횟수 (race contention 충분히)
    private static final int RUNS     = 5;        // 측정 반복 (평균 내기)
    private static final int WARMUP   = 5000;     // JIT 워밍업 횟수

    public static void main(String[] args) throws Exception {

        // === Step 1. JIT 워밍업 (결과 안 봄) ===
        // JVM이 코드를 충분히 실행해야 hot path 최적화가 적용됩니다.
        // 워밍업 없이 측정하면 첫 측정값만 비정상적으로 느립니다.
        System.out.println("[워밍업] " + WARMUP + "회 실행 중...");
        for (int i = 0; i < WARMUP; i++) {
            BankAccount warmup = new BankAccount(100);
            warmup.withdraw(50);
        }
        System.out.println("[워밍업] 완료\n");

        // === Step 2. 3가지 해결책 비교 측정 ===
        System.out.println("=== 해결책 비교 (" + THREADS + "스레드 × " + ATTEMPTS + "번 × " + RUNS + "회 평균) ===\n");

        Result none = measureMultipleRuns(SimpleAccount::new);
        Result sync = measureMultipleRuns(SyncAccount::new);
        Result atom = measureMultipleRuns(AtomicAccount::new);

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

    /**
     * 같은 측정을 RUNS회 반복해서 평균을 냅니다.
     *
     * <p>CountDownLatch로 모든 스레드를 동시 출발 — race contention 극대화.
     * (이거 없으면 그냥 submit이라 200스레드 풀에서 순차적으로 처리되어 race 잘 안 보임)
     */
    private static Result measureMultipleRuns(AccountFactory factory) throws Exception {
        double totalMisses = 0;
        double totalMillis = 0;

        for (int run = 0; run < RUNS; run++) {
            Account account = factory.create();
            ExecutorService executor = Executors.newFixedThreadPool(THREADS);
            AtomicInteger success = new AtomicInteger(0);
            CountDownLatch startSignal = new CountDownLatch(1);

            // 모든 스레드를 대기 상태로 등록
            for (int i = 0; i < ATTEMPTS; i++) {
                executor.submit(() -> {
                    try { startSignal.await(); } catch (InterruptedException e) { return; }
                    if (account.withdraw(1)) {
                        success.incrementAndGet();
                    }
                });
            }

            // 모든 스레드가 latch 대기 도달까지 잠깐
            Thread.sleep(50);

            // 측정 시작 + 동시 출발
            long start = System.nanoTime();
            startSignal.countDown();

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            // 측정 종료
            long elapsed = System.nanoTime() - start;

            // 기대 성공 = 100 (잔고 100, 1원씩 출금이니까 100명 통과 후 잔고 0)
            int misses = Math.abs(success.get() - 100);
            totalMisses += misses;
            totalMillis += elapsed / 1_000_000.0;
        }

        return new Result(totalMisses / RUNS, totalMillis / RUNS);
    }

    // === 인터페이스와 3가지 구현 ===

    interface Account {
        boolean withdraw(int amount);
    }

    interface AccountFactory {
        Account create();
    }

    /**
     * 측정 결과 — 평균 누락 수와 평균 응답시간.
     */
    static class Result {
        final double avgMisses, avgMillis;
        Result(double m, double t) { this.avgMisses = m; this.avgMillis = t; }
    }

    /**
     * 해결책 없음 — race가 그대로 발생합니다.
     * 비교 기준선(baseline)으로 사용.
     */
    static class SimpleAccount implements Account {
        private int balance = 100;
        public boolean withdraw(int amount) {
            if (balance >= amount) {
                balance -= amount;
                return true;
            }
            return false;
        }
    }

    /**
     * 해결책 1 — synchronized 메서드.
     * 한 번에 한 스레드만 메서드 안에 들어갈 수 있어 안전합니다.
     * 단점: 다른 스레드는 줄 서서 기다려야 하므로 응답이 느려집니다.
     */
    static class SyncAccount implements Account {
        private int balance = 100;
        public synchronized boolean withdraw(int amount) {
            if (balance >= amount) {
                balance -= amount;
                return true;
            }
            return false;
        }
    }

    /**
     * 해결책 2 — AtomicInteger의 CAS (Compare And Set, 비교+교체).
     * "내가 본 값이 그대로면 새 값으로 바꿔라" 를 원자적으로 수행.
     * 다른 스레드가 먼저 바꿨으면 false 받고 다시 시도 (낙관적 락).
     * 줄 안 서므로 빠름.
     */
    static class AtomicAccount implements Account {
        private final AtomicInteger balance = new AtomicInteger(100);
        public boolean withdraw(int amount) {
            while (true) {
                int current = balance.get();
                if (current < amount) {
                    return false;
                }
                // CAS: 잔고가 current 그대로면 current - amount 로 바꿈
                if (balance.compareAndSet(current, current - amount)) {
                    return true;
                }
                // 다른 스레드가 먼저 바꿨음 → 다시 시도
            }
        }
    }
}
