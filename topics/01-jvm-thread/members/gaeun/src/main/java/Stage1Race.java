import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Stage1Race {
    public static void main(String[] args) throws Exception {
        UnsafeConcertBooking concertBooking = new UnsafeConcertBooking();

        int requestCount = 1000;
        int threadCount = 200;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger(0);


        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();   // 작업 준비 완료
                    startLatch.await();       // 출발 신호 대기

                    if (concertBooking.reserve()) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();    // 작업 종료
                }
            });
        }

        // 동시에 실행 가능한 threadCount개의 작업이 출발 대기 상태에 도달할 때까지 대기
        readyLatch.await();

        long start = System.nanoTime();

        // 동시에 출발
        startLatch.countDown();

        // 모든 작업 종료 대기
        doneLatch.await();

        long end = System.nanoTime();

        executor.shutdown();

        int expected = 100;
        int actual = successCount.get();
        int overBooked = actual - expected;
        double elapsedMs = (end - start) / 1_000_000.0;

        System.out.println("기대: " + expected + " / 실제: " + actual);
        System.out.println("초과 예약: " + Math.max(overBooked, 0));
        System.out.println("걸린 시간: " + elapsedMs + "ms");

        MeasurementLog.save("s1", "race 재현", "초과", Math.abs(overBooked), elapsedMs);
    }

}
