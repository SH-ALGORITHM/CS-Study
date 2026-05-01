import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * STAGE 4 (선택) — Virtual Threads로 같은 race 재현
 *
 * <h3>Virtual Threads 가 무엇인가</h3>
 * Java 21에 정식으로 들어간 표준 기능. 일반 Thread (Platform Thread) 보다
 * 훨씬 가벼워서 1만 개 이상 만들어도 OS에 부담이 없습니다.
 *
 * <h3>학습 포인트</h3>
 * <ul>
 *   <li>같은 race가 더 격렬하게 발생 (동시성 훨씬 높음)</li>
 *   <li>메모리 모델은 동일 — STAGE 3의 해결책 그대로 적용 가능</li>
 *   <li>응답시간은 어떻게 다른가? 직접 측정해보기</li>
 * </ul>
 *
 * <h3>Spring Boot 필요 없음</h3>
 * Java 21 표준 기능이라 Spring Boot 없이 바로 사용 가능합니다.
 * 4주차+의 STAGE 4는 Spring 통합으로 바뀝니다.
 */
public class Stage4VirtualThreads {

    public static void main(String[] args) throws InterruptedException {

        // 1. 잔고 100원짜리 계좌
        BankAccount account = new BankAccount(100);

        // 2. ⭐ 핵심 차이: Virtual Threads로 ExecutorService 생성
        //    기존: Executors.newFixedThreadPool(50)            ← Platform Thread 50개 풀
        //    변경: Executors.newVirtualThreadPerTaskExecutor() ← 각 작업마다 가상 스레드
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        AtomicInteger successCount = new AtomicInteger(0);

        // 3. 시간 측정 시작
        long start = System.nanoTime();

        // 4. 스레드 수 폭증 — 1만 개
        //    (Platform Thread로는 OS 한계로 못 만듦. Virtual Thread는 가능)
        for (int i = 0; i < 10_000; i++) {
            executor.submit(() -> {
                if (account.withdraw(1)) {
                    successCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // 5. 측정 종료
        long elapsed = System.nanoTime() - start;

        // 6. 결과 출력 (측정 끝난 후에만)
        System.out.println("=== STAGE 4 — Virtual Threads ===");
        System.out.println("스레드: 10,000개 (가상)");
        System.out.println("기대: 성공 100명, 잔고 0원");
        System.out.println("실제: 성공 " + successCount.get() + "명, 잔고 " + account.getBalance() + "원");
        System.out.printf ("시간: %.1fms%n", elapsed / 1_000_000.0);
        System.out.println();

        // 7. 간단 해석
        if (successCount.get() > 100 || account.getBalance() < 0) {
            System.out.println("→ ⚠️ Virtual Threads 환경에서도 race 발생!");
            System.out.println("→ Platform Thread 결과(STAGE 1)와 비교해보세요");
        }

        System.out.println();
        System.out.println("실험 아이디어:");
        System.out.println("- 스레드 수를 10만 개로 늘려보기");
        System.out.println("- STAGE 3의 SyncAccount/AtomicAccount를 Virtual Threads로 측정");
        System.out.println("- Platform Thread 50개 vs Virtual Thread 10000개 응답시간 비교");

        // 8. 자동 기록 — measurements.md 에 한 줄 누적
        System.out.println();
        long elapsedMs = elapsed / 1_000_000;
        MeasurementLog.save("s4", "Virtual Threads (10000개)",
                            Math.abs(successCount.get() - 100), elapsedMs);
    }
}
