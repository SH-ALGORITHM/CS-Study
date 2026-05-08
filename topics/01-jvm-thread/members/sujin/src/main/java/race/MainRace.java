package race;

import common.MeasurementLog;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.util.concurrent.Executors.newFixedThreadPool;

public class MainRace {
    record RaceResult(double missed, double elapsedMs) {}

    public static void main(String[] args) throws InterruptedException {
        RaceResult base = measure5x(() -> new BatchProcessor());
        RaceResult sync = measure5x(() -> new BatchProcessorSync());
        RaceResult atom = measure5x(() -> new BatchProcessorAtomic());

        MeasurementLog.save("s3", "race 재현 (count++)", base.missed(), base.elapsedMs());
        MeasurementLog.save("s3", "race 해결 : synchronized", sync.missed(), sync.elapsedMs());
        MeasurementLog.save("s3", "race 해결 : AtomicInteger", atom.missed(), atom.elapsedMs());
    }

    private static RaceResult measure5x(Supplier<Counter> factory) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            Counter w = factory.get();
            runOnce(w, 50, 1000); // JVM 워밍업
        }

        double[] missedRuns = new double[5];
        double[] timeRuns = new double[5];

        // 측정 5회
        for (int i = 0; i < 5; i++) {
            Counter c = factory.get();
            RaceResult result = runOnce(c, 50, 1000);
            missedRuns[i] = result.missed;
            timeRuns[i] = result.elapsedMs;
        }

        double avgMissed = Arrays.stream(missedRuns).average().orElse(0);
        double avgTime = Arrays.stream(timeRuns).average().orElse(0);

        return new RaceResult(avgMissed, avgTime);
    }

    private static RaceResult runOnce(Counter proc, int threads, int calls) throws InterruptedException {
        ExecutorService executor = newFixedThreadPool(threads);

        long start = System.nanoTime();

        for (int i = 0; i < calls; i++) {
            executor.submit(() -> proc.process());
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long elapsed = System.nanoTime() - start;
        double elapsedMs = elapsed / 1_000_000.0;

        int actual = proc.getProcessedCount();
        int missed = calls - actual;

        return new RaceResult(missed, elapsedMs);
    }
}
