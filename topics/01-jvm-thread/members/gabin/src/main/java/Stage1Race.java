import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * STAGE 1 — race condition 직접 보기
 * 실행 시나리오 : 한 결제에 대해서 두 번의 이중 환불이 발생한 경우
 * 판정 기준
 * 기대 환불 횟수 : 1 -> 실제 환불 횟수 : 2
 * 기대 환불 금액 : 10000 -> 실제 환불 금액 : 20000 이상
 */
public class Stage1Race {

    public static void main(String[] args) throws InterruptedException {
        //환불 서비스 호출
        RefundService refundService = new RefundService();
        //동시에 1000개 스레드 실행
        ExecutorService executor = Executors.newFixedThreadPool(1000);

        AtomicInteger successCount = new AtomicInteger(0);

        //모든 스레드를 같은 시점에서 출발
        CountDownLatch startSignal = new CountDownLatch(1);

        // 1000번 시도
        int totalAttempts = 1000;

        for (int i = 0; i < totalAttempts; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                } catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    if (refundService.refund()) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        Thread.sleep(100);

        long start = System.nanoTime();
        startSignal.countDown();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        //결과
        System.out.println("============= 이중 환불 결과 =============");
        System.out.println("동시 환불 요청: " + totalAttempts + "건");
        System.out.println("기대: 환불 성공 1건 | 환불 금액 " + refundService.getPaymentAmount() + "원");
        System.out.println("실제: 환불 성공 반환 " + successCount.get()
            + "건 | 내부 환불 처리 " + refundService.getRefundCount()
            + "건 | 환불 금액 " + refundService.getRefundedAmount() + "원");
        System.out.println("최종 환불 상태: " + (refundService.isRefunded() ? "환불됨" : "환불 안 됨"));
        System.out.println();

        //판정 : 실제 환불 횟수는 1건만 결처리 되어야 하므로 1보다 이상이면 race condition 발생으로 판정
        if (successCount.get() > 1) {
            System.out.println("race condition 발생");
            System.out.println("여러 스레드가 동시에 if (!refunded)를 통과해 같은 결제 건이 중복 환불됨");
        } else {
            System.out.println("운 좋게 race가 보이지 않음. 재실행 요함.");
        }

        int excessRefunds = Math.max(0, successCount.get() - 1);
        MeasurementLog.save("s1", "baseline-noSync(excessRefunds)", excessRefunds, elapsedMs);
    }
}
