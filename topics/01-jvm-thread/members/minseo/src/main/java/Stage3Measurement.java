import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Stage3Measurement {

    private static final double RUNS = 5.0;

    public static int doTest(int threadCnt, int totalAttempts, boolean isReal, String type) throws Exception{
        Attendance attendance;

        if (type.equals("none")) {
            attendance = new AttendanceNone();
        } else if (type.equals("sync")) {
            attendance = new AttendanceSync();
        } else {
            attendance = new AttendanceAtomic();
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCnt);
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        for(int i = 0; i < totalAttempts; i++) {
            executor.submit(() -> {
                try {
                    latch.await();

                    if (attendance.checkIn()) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        if (isReal) {
            System.out.println("측정 중... 스레드: " + threadCnt);
        } else {

        }

        return successCount.get();
    }

    public static void main(String[] args) throws Exception {

        int[] threads = {10, 50, 100, 1000};
        String[] types = {"none", "sync", "atomic"};

        for (String type : types) {
            for (int thread : threads) {

                // 워밍업
                doTest(thread, 5000, false, type);

                int totalSuccess = 0;
                long totalTime = 0;
                // 측정 시작
                for (int j = 0; j < RUNS; j++) {
                    long start = System.currentTimeMillis();
                    totalSuccess += doTest(thread, 20000, true, type);
                    totalTime += (System.currentTimeMillis() - start);
                }

                double avgSuccess = totalSuccess / RUNS;
                double avgTime = totalTime / RUNS;

                // 기록
                System.out.println("방법: " + type + "스레드 " + thread + "개 | 걸린 시간: " + avgTime + "ms | 출근 찍힌 수: " + avgSuccess);
                MeasurementLog.save("s3", "type-" + type + "thread-count-" + thread, avgSuccess - 1, avgTime);
            }
        }

    }
}
