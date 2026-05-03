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

        Work w = new Work();

        w.start();
        Thread.sleep(1000);
        w.checkOut = true;           // 퇴근 도장을 찍음
        w.join(3000);

        double missCount = w.isAlive() ? 1.0 : 0.0;

        System.out.println("퇴근 도장이 찍히지 않았습니다.");
        MeasurementLog.save("s2", "visibility 재현 (volatile X, 빈 루프)", missCount, 0.0);
    }
}
