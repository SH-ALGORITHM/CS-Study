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
        System.out.println("=".repeat(50));
        System.out.println("🔍 STAGE 2: 가시성(Visibility) 재현 테스트 시작");
        System.out.println("=".repeat(50));

        Work w = new Work();
        w.start();

        System.out.println("1. 일꾼 스레드 시작 (무한 루프 감시 중...)");
        Thread.sleep(1000); // 1초 대기

        System.out.println("2. 메인 스레드: checkOut = true 변경 (퇴근 신호)");
        long startTime = System.currentTimeMillis();
        w.checkOut = true;

        System.out.println("3. 일꾼이 신호를 보는지 3초간 대기합니다...");
        w.join(3000); // 3초 타임아웃 대기

        long duration = System.currentTimeMillis() - startTime;
        boolean isBugFound = w.isAlive(); // 3초 뒤에도 살아있으면 버그(가시성 문제) 발생!

        if (isBugFound) {
            System.out.println("\n❌ [결과] 가시성 위반 발생!");
            System.out.println("   - 사유: 메인 스레드가 값을 바꿨지만, 일꾼 스레드는 캐시된 이전 값(false)만 보고 있음.");
            System.out.println("   - 상태: 일꾼 스레드가 3초가 지나도 종료되지 않음.");

            // 누락(misses)에 1.0을 넣어 "문제 발생"을 표시합니다.
            MeasurementLog.save("s2", "Visibility-Repro (No Volatile)", 1.0, (double) duration);
        } else {
            System.out.println("\n✅ [결과] 가시성 문제 해결!");
            System.out.println("   - 사유: 일꾼 스레드가 변경된 값을 즉시 확인하고 정상 종료됨.");
            System.out.println("   - 소요 시간: " + duration + "ms");

            // 누락(misses)에 0.0을 넣어 "정상"을 표시합니다.
            MeasurementLog.save("s2", "Visibility-Resolved (Volatile Applied)", 0.0, (double) duration);
        }

        System.out.println("=".repeat(50));
    }
}
