import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Stage3Measurement {

    record Result(double elapsedMs, int overBooked) {}
    record Key(String label, int threadCnt) {}

    static LinkedHashMap<Key, List<Result>> result = new LinkedHashMap<>();
    static final int REQUESTS_PER_THREAD = 1000;

    public static void main(String[] args) throws Exception {
        // 워밍업 — JIT 안정화
        System.out.println("=== Warmup ===");
        for (int i = 0; i < 3; i++) {
            int warmupSeats = 5000;
            measure("WarmupUnsafe", new UnsafeConcertBooking(warmupSeats), 100, warmupSeats);
            measure("WarmupSynchronized", new SynchronizedConcertBooking(warmupSeats), 100, warmupSeats);
            measure("WarmupAtomic", new AtomicConcertBooking(warmupSeats), 100, warmupSeats);
        }
        result.clear();   // 워밍업 결과 버림 (만약 measure 가 result 에 쓰면)

        System.out.println("\n=== 본 측정 ===");

        int retryCnt = 5;
        int[] threadCntArr = new int[]{10, 50, 100, 1000};

        // UnsafeConcertBooking 측정
        for (int run = 0; run < retryCnt; run++) {
            for (int threadCnt : threadCntArr) {
                int seats = (threadCnt * REQUESTS_PER_THREAD) / 2;
                measure("Unsafe", new UnsafeConcertBooking(seats), threadCnt, seats);
                measure("Synchronized", new SynchronizedConcertBooking(seats), threadCnt, seats);
                measure("Atomic", new AtomicConcertBooking(seats), threadCnt, seats);
            }

        }


        printSummary();

    }

    private static void measure(String label, ConcertBooking booking, int threadCnt, int seats) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCnt);
        CountDownLatch readyLatch = new CountDownLatch(threadCnt);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCnt);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCnt; i++) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                        if (booking.reserve()) successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        long start = System.nanoTime();
        startLatch.countDown();
        doneLatch.await();
        long elapsedNs = System.nanoTime() - start;

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        int actual = successCount.get();
        int overBooked = Math.max(0, actual - seats);
        double elapsedMs = elapsedNs / 1_000_000.0;

        Key key = new Key(label, threadCnt);
        result.computeIfAbsent(key, k -> new ArrayList<>())
            .add(new Result(elapsedMs, overBooked));
    }

    private static void printSummary() {
        System.out.printf("%-15s %5s | %10s %10s %10s | %10s%n",
            "label", "thr", "avgMs", "minMs", "maxMs", "avgOver");
        System.out.println("-".repeat(75));

        for (var entry : result.entrySet()) {
            var key = entry.getKey();
            var list = entry.getValue();

            double avgMs = list.stream().mapToDouble(Result::elapsedMs).average().orElse(0);
            double minMs = list.stream().mapToDouble(Result::elapsedMs).min().orElse(0);
            double maxMs = list.stream().mapToDouble(Result::elapsedMs).max().orElse(0);
            double avgOver = list.stream().mapToInt(Result::overBooked).average().orElse(0);

            System.out.printf("%-15s %5d | %10.2f %10.2f %10.2f | %10.2f%n",
                key.label(), key.threadCnt(), avgMs, minMs, maxMs, avgOver);

            // measurements.md 에 평균 한 줄 누적
            MeasurementLog.save(
                "s3",
                key.label() + " thr=" + key.threadCnt(),
                "초과예약",
                avgOver,
                avgMs
            );
        }
    }
}
