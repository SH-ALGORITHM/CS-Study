import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 1주차 학습 시작점.
 * <p>
 * 다음 순서로 진행하세요:
 * 1) topics/01-jvm-thread/scenario.md 읽기
 * 2) 도메인 1개 선택 (쿠폰 / 좌석 / 재고 / 좋아요 / 출퇴근 / 환불 / volatile)
 * 3) 본인 도메인 클래스 만들기 (예: CouponEvent.java)
 * 4) STAGE 1 race 재현 코드 작성 (이 Stage1Race.java 안 또는 별도 파일에)
 * 5) IntelliJ에서 이 main 메서드 옆 ▶ 클릭하면 실행됨
 * <p>
 * 참고 코드: topics/01-jvm-thread/example/ (은행 잔고 도메인 — 베끼지 말고 흐름만 참고)
 */
public class Stage1Race {
    public static void main(String[] args) throws Exception {
        ConcertBooking concertBooking = new ConcertBooking();

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
