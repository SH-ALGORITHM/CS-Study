/**
 * STAGE 2 — visibility (가시성) 위반 직접 보기
 * 실횅 시나리오 : 환불 마감 시간에 임박했는데, 환불 처리 중인 스레드가 마감을 못 봐서 마감 후에도 계속 환불 처리 하는 경우
 * flag 변수 : refundCutoffReached
 * Worker 클래스 : RefundProcessor
 */
public class Stage2VisibilityRefund {

    static class RefundProcessor extends Thread {

        volatile boolean refundCutoffReached = false; //환불 마감 시각 도달 flag 변수

        @Override
        public void run() {
            while (!refundCutoffReached) {
                // 빈 루프
            }

            // 여기에 도달하면 정상 종료. 못하면 visibility 위반으로 영원히 못 멈춤. (daemon 설정해서 main 종료 시 JVM도 종료되도록 설정
        }
    }

    public static void main(String[] args) throws InterruptedException {

        RefundProcessor refundProcessor = new RefundProcessor();
        refundProcessor.setDaemon(true);

        long start = System.nanoTime();
        refundProcessor.start();

        Thread.sleep(1000);
//        Thread.sleep(10);

        System.out.println("마감 시각 도달: refundCutoffReached = true 설정");
        refundProcessor.refundCutoffReached = true;

        refundProcessor.join(5000);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // 5. 결과 판정
        boolean visibilityViolation = refundProcessor.isAlive();
        if (visibilityViolation) {
            System.out.println("→ RefundProcessor가 5초 후에도 안 멈춤. visibility 위반 발생!");
            System.out.println("→ 다른 스레드가 refundCutoffReached = true 한 게 RefundProcessor에게 안 보임");
            System.out.println("→ RefundProcessor는 daemon thread라 main 종료와 함께 자동 종료됨");
        } else {
            System.out.println("→ RefundProcessor 정상 종료 (visibility 위반이 안 보임)");
            System.out.println("→ 위 javadoc의 '안 보일 때' 4단계 시도해보세요");
            System.out.println("→ -Xint 옵션이 가장 확실히 보입니다");
        }

        System.out.println();
        MeasurementLog.save("s2", "stop flag visibility (volatile 없음)",
            visibilityViolation ? 1.0 : 0.0, elapsedMs);

    }
}
