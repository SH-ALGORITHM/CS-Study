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
        int rounds = 200; // 라운드 수
        int pool_size = 8; //노트북 cpu 수
        int totalExcess =0;
        int raceRoundCount =0;
        int totalElapsedMs = 0;

        for (int r=0; r<rounds; r++){
            RefundService refundService = new RefundService();
            ExecutorService executor = Executors.newFixedThreadPool(pool_size);

            AtomicInteger successCount = new AtomicInteger(0);

            //모든 스레드를 같은 시점에서 출발
            CountDownLatch startSignal = new CountDownLatch(1);

            for (int i = 0; i < pool_size; i++) {
                executor.submit(() -> {
                    try {
                        startSignal.await();
                    } catch (InterruptedException e){
                        Thread.currentThread().interrupt();
                        return;
                    }
                    //sleep() 사용 안 하므로 에러 안 던짐. 따라서 트라이캐치 불필요
                    try {
                        if (refundService.refund()) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            Thread.sleep(20);

            long start = System.nanoTime();
            startSignal.countDown();

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;


            //판정 : 실제 환불 횟수는 1건만 결처리 되어야 하므로 1보다 이상이면 race condition 발생으로 판정
            if (successCount.get() > 1) {
                System.out.println("라운드 " + r + ": race 발생 — successCount=" + successCount.get());
            }

            totalExcess += Math.max(0, successCount.get() - 1);
            if (successCount.get() > 1) raceRoundCount++;
            totalElapsedMs += elapsedMs;
        }

        System.out.println("=== s1 결과 (sleep 없음, 풀=" + pool_size + ", rounds=" + rounds + ") ===");
        System.out.println("race 발생 라운드: " + raceRoundCount + "/" + rounds);
        System.out.println("누적 초과 환불: " + totalExcess + "건");
        System.out.println("총 측정 시간: " + totalElapsedMs + "ms");

        MeasurementLog.save("s1", "baseline-noSync-sleep10ms-pool8-rounds200", totalExcess, totalElapsedMs);
    }
}
