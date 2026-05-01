import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * STAGE 1 — race condition 직접 보기
 *
 * <h3>실행 시나리오</h3>
 * <ul>
 *   <li>잔고 100원짜리 계좌 1개</li>
 *   <li>1000명(스레드)이 동시에 1원씩 출금 시도</li>
 *   <li>기대: 정확히 100명만 성공 (잔고 100 / 1원씩 = 100명)</li>
 *   <li>실제: race 때문에 100명보다 더 많이 성공 → 100원으로 100원 이상 출금됨</li>
 * </ul>
 *
 * <h3>실행 결과 예시</h3>
 * <pre>
 *   기대: 성공 100명, 잔고 0원
 *   실제: 성공 124명, 잔고 -24원   ← race condition 발생!
 * </pre>
 *
 * <h3>race가 잘 보이도록 한 트릭</h3>
 * CountDownLatch로 모든 스레드가 동시에 출발하도록 합니다.
 * 그냥 submit만 하면 스레드들이 순차적으로 시작돼서 race가 잘 안 보입니다.
 *
 * <h3>실행 방법</h3>
 * IntelliJ에서 main 메서드 옆 ▶ 클릭. 결과는 콘솔에서 확인.
 */
public class Stage1Race {

    public static void main(String[] args) throws InterruptedException {

        // 1. 잔고 100원짜리 계좌
        BankAccount account = new BankAccount(100);

        // 2. 스레드 풀 — 동시에 200개 스레드 실행 가능
        ExecutorService executor = Executors.newFixedThreadPool(200);

        // 3. 결과를 셀 카운터
        //    ⚠️ AtomicInteger는 "결과 세는 도구"일 뿐.
        //    race condition 해결용이 아닙니다.
        //    본인 도메인 코드에서는 STAGE 3 전까지 사용하지 마세요.
        AtomicInteger successCount = new AtomicInteger(0);

        // 4. CountDownLatch — 모든 스레드를 같은 시점에 출발시키기 위한 신호
        //    이거 없으면 스레드들이 순차적으로 시작돼서 race가 잘 안 보임
        CountDownLatch startSignal = new CountDownLatch(1);

        // 5. 1000명을 미리 대기 상태로 등록
        int totalAttempts = 1000;
        for (int i = 0; i < totalAttempts; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();   // ← 출발 신호 기다림 (대기)
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (account.withdraw(1)) {
                    successCount.incrementAndGet();
                }
            });
        }

        // 6. 모든 스레드가 latch 대기 상태에 도달할 때까지 잠깐 기다림
        Thread.sleep(100);

        // 7. 출발 신호 — 1000명이 동시에 withdraw() 호출 시작 + 시간 측정 시작
        long start = System.nanoTime();
        startSignal.countDown();

        // 8. 모든 작업이 끝날 때까지 대기
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // 9. 측정/작업이 끝난 후에만 결과 출력
        //    ⚠️ 작업 중간(executor.submit 안)에 println 절대 X.
        //    출력 한 줄에 동기화 효과가 있어 race를 가립니다.
        System.out.println("=== STAGE 1 — race condition 결과 ===");
        System.out.println("초기 잔고: 100원");
        System.out.println("동시 출금 시도: " + totalAttempts + "명 (1원씩)");
        System.out.println();
        System.out.println("기대: 성공 100명, 잔고 0원");
        System.out.println("실제: 성공 " + successCount.get() + "명, 잔고 " + account.getBalance() + "원");
        System.out.printf ("시간: %dms%n", elapsedMs);
        System.out.println();

        // 10. race condition이 발생했는지 판정
        int diff = successCount.get() - 100;
        if (diff != 0 || account.getBalance() != 0) {
            System.out.println("→ ⚠️ race condition 발생!");
            if (diff > 0) {
                System.out.println("→ 100원만 있는데 " + successCount.get() + "원이 출금되어버림");
                System.out.println("→ 잔고가 " + account.getBalance() + "원 (음수)으로 떨어짐");
            }
            System.out.println();
            System.out.println("→ 두 스레드가 동시에 'if (balance >= 1)' 통과 후 둘 다 'balance--' 한 결과");
        } else {
            System.out.println("→ 운 좋게 안 발생. 다시 실행해보세요.");
        }

        // 11. 자동 기록 — measurements.md 에 한 줄 누적
        System.out.println();
        MeasurementLog.save("s1", "race 재현 (200스레드 × 1000회)", Math.abs(diff), elapsedMs);
    }
}
