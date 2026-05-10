import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("1주차 S3 — AtomicInteger 재고 race 해결");

        int totalAttempts = 1000;
        int expectedSuccess = 100;
        int successCount = 0;
        int initialStock = 100;

        ExecutorService executor = Executors.newFixedThreadPool(totalAttempts);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();

        StockMMS stockMMS = new StockMMS(initialStock);

        /*
        1. purchase(1)이 true면 성공 횟수 증가
        2. executor.shutdown()
        3. 모든 작업 끝날 때까지 대기
        4. 실제 성공 수 출력
        5. stockMMS.getStock()으로 남은 재고 출력
         */
        for (int i = 0; i < totalAttempts; i++) {
            futures.add(
                executor.submit(() -> {
                    startLatch.await();
                    return stockMMS.purchase(1);
                })
            );
        }

        // 시간 측정 시작
        long start = System.nanoTime();

        startLatch.countDown();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 시간 계산
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        for (Future<Boolean> f : futures) {
            if(f.get()) {
                successCount++;
            }
        }

        System.out.println("초기 재고: " + initialStock);
        System.out.println("구매 시도: " + totalAttempts);
        System.out.println("기대 성공: " + expectedSuccess);
        System.out.println("실제 성공: " + successCount);
        System.out.println("남은 재고: " + stockMMS.getStock());
        System.out.println("소요 시간(ms): " + elapsedMs);

        // Atomic
        MeasurementLog.save("s3", "atomic", Math.abs(successCount - expectedSuccess), elapsedMs);


        // synchronized
        // MeasurementLog.save("s3", "synchronized", Math.abs(successCount - expectedSuccess), elapsedMs);
    }
}
