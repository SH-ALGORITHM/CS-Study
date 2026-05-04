public class Stage2Visibility {

    static class TicketSeller extends Thread {

        boolean soldOut = false;

        @Override
        public void run() {
            while (!soldOut) {
                // 티켓 판매 중
            }
        }
    }


    public static void main(String[] args) throws InterruptedException {

        TicketSeller seller = new TicketSeller();
        seller.setDaemon(true);

        long start = System.nanoTime();
        seller.start();

        Thread.sleep(1000); // seller가 while 루프에 진입하도록 main 스레드 대기
        seller.soldOut = true;

        seller.join(5000); // seller가 정상 종료되는지 최대 5초 동안 대기

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        boolean visibilityViolation = seller.isAlive(); // true -> visibility 문제 발생

        System.out.println("[main] 매진 처리 (soldOut = true)");
        if (visibilityViolation) {
            System.out.println("-> 이미 매진인데도 계속 판매 중 (visibility 문제)");
        } else {
            System.out.println("-> 판매 정상 종료");
        }

        // visibility 위반 여부: 1 = 위반 발생, 0 = 정상
        System.out.println();
        MeasurementLog.save("s2", "stop flag visibility (volatile 없음)", "위반", visibilityViolation ? 1.0 : 0.0, elapsedMs);

    }
}
