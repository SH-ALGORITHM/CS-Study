package visibility;

import common.MeasurementLog;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * 1주차 학습 시작점.
 *
 * 다음 순서로 진행하세요:
 *   1) topics/01-jvm-thread/scenario.md 읽기
 *   2) 도메인 1개 선택 (쿠폰 / 좌석 / 재고 / 좋아요 / 출퇴근 / 환불 / volatile)
 *   3) 본인 도메인 클래스 만들기 (예: CouponEvent.java)
 *   4) STAGE 1 race 재현 코드 작성 (이 visibility.Main.java 안 또는 별도 파일에)
 *   5) IntelliJ에서 이 main 메서드 옆 ▶ 클릭하면 실행됨
 *
 * 참고 코드: topics/01-jvm-thread/example/ (은행 잔고 도메인 — 베끼지 말고 흐름만 참고)
 */
public class Main {

    record VisibilityResult(double stoppedFlag, double abortToJoinMs) {}

    public static void main(String[] args) throws InterruptedException {
        VisibilityResult base = measure5xVisibility(() -> new BatchJob());
        VisibilityResult vol  = measure5xVisibility(() -> new BatchJobVolatile());
        VisibilityResult sync = measure5xVisibility(() -> new BatchJobSync());
        VisibilityResult atom = measure5xVisibility(() -> new BatchJobAtomBool());

        MeasurementLog.save("s2", "visibility 재현", base.stoppedFlag(), base.abortToJoinMs());
        MeasurementLog.save("s3", "visibility 해결 : volatile", vol.stoppedFlag(), vol.abortToJoinMs());
        MeasurementLog.save("s3", "visibility 해결 : synchronized", sync.stoppedFlag(), sync.abortToJoinMs());
        MeasurementLog.save("s3", "visibility 해결 : atomicBoolean", atom.stoppedFlag(), atom.abortToJoinMs());
    }

    private static VisibilityResult measure5xVisibility(Supplier<AbortableJob> factory) throws InterruptedException {
        double[] stoppedRuns = new double[5];
        double[] timeRuns = new double[5];

        for (int i = 0; i < 5; i++) {
            VisibilityResult result = runOnceVisibility(factory);
            stoppedRuns[i] = result.stoppedFlag();
            timeRuns[i] = result.abortToJoinMs();
        }

        double avgStopped = Arrays.stream(stoppedRuns).average().orElse(0);
        double avgTime = Arrays.stream(timeRuns).average().orElse(0);

        return new VisibilityResult(avgStopped, avgTime);
    }

    private static VisibilityResult runOnceVisibility(Supplier<AbortableJob> factory) throws InterruptedException {
        AbortableJob job = factory.get();
        Thread t = new Thread(job); // 배치 작업을 별도 스레드에서 실행
        t.setDaemon(true); // 무한루프가 JVM 종료 막지 않도록

        t.start();
        Thread.sleep(100); // 워커가 충분히 돌도록

        long abortStart = System.nanoTime();
        job.abort();
        t.join(2000); // 최대 2초 대기

        long elapsed = System.nanoTime() - abortStart;
        boolean stopped = !t.isAlive();

        return new VisibilityResult(stopped ? 0 : 1, elapsed / 1_000_000.0);
    }
}
