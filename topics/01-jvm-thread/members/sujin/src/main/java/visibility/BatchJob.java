package visibility;

/**
 * 배치 작업 중단 플래그 — Visibility 문제 & 해결
 *
 * 시나리오: 야간 배치(정산, 메일 발송 등) 실행 중
 * 오류 발생으로 중단 신호를 보내도 배치 스레드가
 * 캐시된 false를 읽어 계속 실행되는 문제
 */
public class BatchJob implements AbortableJob {

    // ⚠️ 문제: 가시성 미보장 — CPU 캐시에 머물 수 있음
    private boolean aborted = false;

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
