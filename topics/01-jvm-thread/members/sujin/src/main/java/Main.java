/**
 * 1주차 학습 시작점.
 *
 * 다음 순서로 진행하세요:
 *   1) topics/01-jvm-thread/scenario.md 읽기
 *   2) 도메인 1개 선택 (쿠폰 / 좌석 / 재고 / 좋아요 / 출퇴근 / 환불 / volatile)
 *   3) 본인 도메인 클래스 만들기 (예: CouponEvent.java)
 *   4) STAGE 1 race 재현 코드 작성 (이 Main.java 안 또는 별도 파일에)
 *   5) IntelliJ에서 이 main 메서드 옆 ▶ 클릭하면 실행됨
 *
 * 참고 코드: topics/01-jvm-thread/example/ (은행 잔고 도메인 — 베끼지 말고 흐름만 참고)
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        BatchJob batch = new BatchJob();

        // 배치 작업을 별도 스레드에서 실행
        Thread batchThread = new Thread(batch);
        batchThread.setName("batch-worker");
        batchThread.start();

        // 메인 스레드: 5000ms 후 오류 감지 → 중단 신호
        Thread.sleep(5000);
        batch.abort();

        // 배치 스레드가 완전히 종료될 때까지 최대 2초 대기
        batchThread.join(2000);

        System.out.println("[메인] 배치 스레드 살아있음: " + batchThread.isAlive());

        MeasurementLog.save("s2", "visibility 재현 (volatile X, 빈 루프)",
            batchThread.isAlive() ? 1 : 0,  // 1 = visibility 발생
            0);
    }
}
