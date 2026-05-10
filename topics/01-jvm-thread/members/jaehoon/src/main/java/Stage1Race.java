import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Stage1Race {

    public static void main(String[] args) throws InterruptedException {

        LikeCount counter = new LikeCount();

        ExecutorService executor = Executors.newFixedThreadPool(200);


        CountDownLatch startSignal = new CountDownLatch(1);

        int totalAttempts = 1000;
        for (int i = 0; i < totalAttempts; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                counter.withdraw();

            });
        }
        Thread.sleep(100);

        long start = System.nanoTime();
        startSignal.countDown();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("=== STAGE 1 — race condition 결과 ===");
        System.out.println("최대 좋아요: 1000");
        System.out.println("동시 좋아요 시도: " + totalAttempts + "명");
        System.out.println();
        System.out.println("기대: count = " + totalAttempts );
        System.out.println("실제: count = "  + counter.getBalance());
        System.out.printf ("시간: %dms%n", elapsedMs);
        System.out.println();

        int diff = totalAttempts - counter.getBalance();;
            if (diff != 0 ) {
            System.out.println("-> race condition 발생!");
            if (diff > 0) {
                System.out.println("-> 1000명만 성공해야 하는데 " + counter.getBalance() + "만 카운트됨");
                System.out.println(" -> " + diff + "개 누락!");
            }
            System.out.println();
            System.out.println("-> 두 스레드가 동시에 'if (count <100 )' 통과 후 둘 다 'count++' 한 결과");
        } else {
            System.out.println("-> 운 좋게 안 발생. 다시 실행해보세요.");
        }

        System.out.println();
        MeasurementLog.save("s1", "race 재현 (200스레드 × 1000회)", Math.abs(diff), elapsedMs);
    }
}




