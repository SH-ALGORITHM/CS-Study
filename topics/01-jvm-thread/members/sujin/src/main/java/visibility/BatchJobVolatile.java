package visibility;

/**
 * volatile 도입
 */
public class BatchJobVolatile implements AbortableJob {

    private volatile boolean aborted = false;

    @Override
    public void run() {
        System.out.println("[배치] 작업 시작");

        while (!aborted) {

        } // 워커는 캐시값 false를 계속 봄

        System.out.println("[배치] 중단 감지 → 종료");
    }

    public void abort() {
        System.out.println("[감지] ❌ 오류 발생! 중단 신호 발송");
        aborted = true;
    }
}
