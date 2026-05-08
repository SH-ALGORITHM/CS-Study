import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Stage1Race {
    public static void main(String[] args) throws InterruptedException {
        // 쿠폰 객체 - race가 일어날 대상
        CouponEvent event = new CouponEvent(100);
        // 200개 스레드
        ExecutorService executor = Executors.newFixedThreadPool(200);
        // 결과 카운터 - 측정도구
        AtomicInteger successCount = new AtomicInteger(0);
        // 출발 신호 - 카운터 1로 시작
        CountDownLatch startSignal = new CountDownLatch(1);

        // 1000개 작업을 큐에 적재 (각 작업에서 출발선 대기(latch))
        int totalAttempts = 1000;
        for (int i = 0; i < totalAttempts; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await(); // 출발선에서 대기
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (event.claim(1)) { // 신호 받으면 쿠폰 차감
                    successCount.incrementAndGet();
                }
            });
        }

        // 모든 스레드가 await에 도달할 시간을 주기위해 스레드 정지
        Thread.sleep(100);

        // 시간 측정 시작 + 동시 출발 신호
        long start = System.nanoTime();
        startSignal.countDown();

        // 다 끝날 때까지 대기 (최대 10초)
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        long end = (System.nanoTime() - start) / 1_000_000;

        System.out.println("=== STAGE 1 — race condition 결과 ===");
        System.out.println("초기 쿠폰 갯수: 100개");
        System.out.println("동시 차감 시도: " + totalAttempts + "명 (1개씩)");
        System.out.println();
        System.out.println("기대: 성공 100개, 잔여 0개");
        System.out.println("실제: 성공 " + successCount.get() + "개, 잔여 " + event.getAvailableCoupons() + "개");
        System.out.printf("시간: %dms%n", end);
        System.out.println();

        int diff = successCount.get() - 100;
        if (diff != 0 || event.getAvailableCoupons() != 0) {
            System.out.println("→ ⚠️ race condition 발생!");
            if (diff > 0) {
                System.out.println("→ 100개만 있는데 " + successCount.get() + "개가 발행되어버림");
                System.out.println("→ 잔여가 " + event.getAvailableCoupons() + "개 (음수)로 떨어짐");
            }
            System.out.println();
            System.out.println("→ 두 스레드가 동시에 'if (event >= 1)' 통과 후 둘 다 'event--' 한 결과");
        } else {
            System.out.println("→ 운 좋게 안 발생. 다시 실행해보세요.");
        }

        System.out.println();
        MeasurementLog.save("s1", "race 재현 (200스레드 x 1000회)", Math.abs(diff), end);
    }
}
