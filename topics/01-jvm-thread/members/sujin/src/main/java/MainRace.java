import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newFixedThreadPool;

public class MainRace {
    public static void main(String[] args) throws InterruptedException {
        BatchProcessor processor = new BatchProcessor();

        // 측정 시작
        long start = System.nanoTime();

        // ⚠️ 고정 스레드 풀 50개
        // → CPU 코어 수보다 많을 경우 context switching 증가
        // → 의도: 실제 병렬성 + 경쟁 상황을 강제로 유도
        ExecutorService executor = newFixedThreadPool(50);

        // ⚠️ 작업 1000개 제출
        // → 동일한 공유 변수(processedCount)에 대한 경쟁 발생
        // → 호출 횟수를 늘릴수록 race 발생 확률 ↑
        for (int i = 0; i < 1000; i++) {
            executor.submit(() -> processor.process());
        }

        executor.shutdown();

        // ⚠️ 모든 작업 완료까지 대기
        // → timeout 내 완료 안 되면 일부 작업 누락 가능 (측정 왜곡 주의)
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 측정 종료
        long elapsed = System.nanoTime() - start;
        double elapsedMs = elapsed / 1_000_000.0;

        int actual = processor.getProcessedCount();
        int missed = 1000 - actual;

        // ⚠️ 기대값 vs 실제값
        // → 누락된 수 = lost update 발생 횟수
        // → processedCount++ (RMW)가 원자적이지 않기 때문
        System.out.println("기대: 1000 / 실제: " + actual + " / 누락: " + missed);
        MeasurementLog.save("s1", "race 재현 (count++ RMW)", missed, elapsedMs);
    }
}
