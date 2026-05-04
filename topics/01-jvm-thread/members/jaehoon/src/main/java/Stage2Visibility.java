import java.util.concurrent.atomic.AtomicInteger;

public class Stage2Visibility {
    static class Worker extends Thread {

        boolean closed = false;

        @Override
            public void run() {
            while (!closed) {

            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Worker w = new Worker();
        w.setDaemon(true);
        long start = System.nanoTime();
        w.start();

        Thread.sleep(1000);

        System.out.println("[main] closed = true 설정 (좋아요 마감)");
        w.closed = true;

        w.join(5000);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        boolean visibilityViolation = w.isAlive();
        if (visibilityViolation) {
            System.out.println("-> Worker가 5초 후에도 안 멈춤. visibility 위반 발생!");
            System.out.println("-> 다른 스레드가 stopped = true 한 게 Worker에게 안 보임");
            System.out.println("-> Worker는 daemon thread라 main 종료와 함께 자동 종료됨");
        } else {
            System.out.println("-> Worker 정상 종료 (visibility 위반이 안 보임)");
            System.out.println("-> 위 javadoc의 '안 보일 때' 4단계 시도해보세요");
            System.out.println("-> -Xint 옵션이 가장 확실히 보입니다");
        }

        System.out.println();
        MeasurementLog.save("s2", "stop flag visibility (volatile 없음)",
            visibilityViolation ? 1.0 : 0.0, elapsedMs);
    }
}
