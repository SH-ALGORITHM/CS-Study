public class Stage2Visibility {

    static class Worker extends Thread {
        boolean stopped = false;

        @Override
        public void run() {
            while (!stopped) {

            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Worker w = new Worker();
        w.setDaemon(true); // daemon = main (끝나면 자동 종료) - 가시성 위반 시 무한루프 방지
        // 시간 측정 시작
        long start = System.nanoTime();
        w.start(); // worker 스레드 시작

        // 워밍업 - JIT 컴파일러 발동 시간
        Thread.sleep(100);

        w.stopped = true; // 무한루프 종료 시도

        // worker 끝날 때까지 최대 5초 대기
        w.join(5000);
        long end = (System.nanoTime() - start) / 1_000_000;

        // 5초 후에도 살아있으면 = 가시성 위반
        boolean visibilityViolation = w.isAlive();
        if (visibilityViolation) {
            System.out.println("→ ⚠️ Worker가 5초 후에도 안 멈춤. visibility 위반 발생!");
            System.out.println("→ 다른 스레드가 stopped = true 한 게 Worker에게 안 보임");
            System.out.println("→ Worker는 daemon thread라 main 종료와 함께 자동 종료됨");
        } else {
            System.out.println("→ Worker 정상 종료 (visibility 위반이 안 보임)");
            System.out.println("→ -Xint 옵션이 가장 확실히 보입니다");
        }

        System.out.println();
        MeasurementLog.save("s2", "stop flag visibility (volatile 없음)",
            visibilityViolation ? 1.0 : 0.0, end);

    }
}
