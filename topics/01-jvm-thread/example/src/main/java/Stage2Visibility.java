/**
 * STAGE 2 — visibility (가시성) 위반 직접 보기
 *
 * <h3>visibility 가 무엇인가</h3>
 * 한 스레드가 변수 값을 바꿨는데, 다른 스레드가 그 변경을 못 보는 현상.
 * CPU 캐시 / JVM 최적화 때문에 발생합니다.
 *
 * <h3>실행 시나리오</h3>
 * <ul>
 *   <li>Worker 스레드는 stopped 깃발이 true 될 때까지 무한 루프</li>
 *   <li>main 스레드가 1초 후에 stopped = true 시도</li>
 *   <li>그런데... Worker가 영원히 안 멈출 수 있음 (visibility 위반)</li>
 * </ul>
 *
 * <h3>안 보일 때 (자주 그럼) — 순서대로 시도</h3>
 * <ol>
 *   <li>println 같은 출력문을 다 제거했는지? (출력 한 줄에 동기화 효과 있음)</li>
 *   <li>Thread.sleep 시간을 더 늘리기 (워밍업 더 충분히)</li>
 *   <li><b>-Xint 옵션으로 인터프리터 모드 강제 — 가장 잘 보임</b><br>
 *       IntelliJ: Run → Edit Configurations → VM options 에 "-Xint" 추가</li>
 *   <li>그래도 안 보이면 운영자 mention. 재현 안 되는 것도 학습 자료.</li>
 * </ol>
 */
public class Stage2Visibility {

    /**
     * stop flag로 작동하는 Worker 스레드.
     *
     * <p>⚠️ stopped 필드는 의도적으로 volatile 안 붙였습니다.
     * STAGE 2에서 visibility 위반을 보기 위함입니다.
     * STAGE 3에서 volatile / synchronized 등으로 해결하게 됩니다.
     */
    static class Worker extends Thread {

        // ⚠️ volatile 없는 일반 boolean
        boolean stopped = false;

        @Override
        public void run() {
            // ⚠️ tight loop — 다른 작업 없이 깃발만 본다.
            //    안에 println 같은 출력 절대 X.
            //    출력 한 줄에 동기화 효과가 있어 visibility 문제가 가려집니다.
            while (!stopped) {
                // 빈 루프 (의도적으로)
            }

            // 여기에 도달하면 정상 종료.
            // 도달 못하면 → visibility 위반으로 영원히 못 멈춤.
        }
    }

    public static void main(String[] args) throws InterruptedException {

        // 1. Worker 스레드 시작 (daemon — main 종료 시 JVM도 함께 종료)
        Worker w = new Worker();
        w.setDaemon(true);
        long start = System.nanoTime();
        w.start();

        // 2. 1초 대기 — Worker가 충분히 워밍업되도록
        Thread.sleep(1000);

        // 3. main 스레드에서 "멈춰" 신호 보냄
        System.out.println("[main] stopped = true 설정");
        w.stopped = true;

        // 4. Worker가 멈추기를 최대 5초 기다림
        w.join(5000);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // 5. 결과 판정
        boolean visibilityViolation = w.isAlive();
        if (visibilityViolation) {
            System.out.println("→ ⚠️ Worker가 5초 후에도 안 멈춤. visibility 위반 발생!");
            System.out.println("→ 다른 스레드가 stopped = true 한 게 Worker에게 안 보임");
            System.out.println("→ Worker는 daemon thread라 main 종료와 함께 자동 종료됨");
        } else {
            System.out.println("→ Worker 정상 종료 (visibility 위반이 안 보임)");
            System.out.println("→ 위 javadoc의 '안 보일 때' 4단계 시도해보세요");
            System.out.println("→ -Xint 옵션이 가장 확실히 보입니다");
        }

        // 6. 자동 기록 — visibility 위반 여부를 1/0으로
        //    misses 컬럼: 1 = 위반 발생, 0 = 정상
        //    millis 컬럼: main 시작부터 종료까지 걸린 시간
        System.out.println();
        MeasurementLog.save("s2", "stop flag visibility (volatile 없음)",
                            visibilityViolation ? 1.0 : 0.0, elapsedMs);

        // main 종료 → daemon Worker도 자동 종료 → JVM 종료
    }
}
