package visibility;

/**
 * 메서드를 synchronized로 선언
 */
public class BatchJobSync implements AbortableJob {

    private boolean aborted = false;

    @Override
    public void run() {
        System.out.println("[배치] 작업 시작");
        while (!isAborted()) { }
        System.out.println("[배치] 중단 감지 → 종료");
    }

    private synchronized boolean isAborted() {
        return aborted;
    }

    public synchronized void abort() {
        System.out.println("[감지] ❌ 오류 발생! 중단 신호 발송");
        aborted = true;
    }
}
