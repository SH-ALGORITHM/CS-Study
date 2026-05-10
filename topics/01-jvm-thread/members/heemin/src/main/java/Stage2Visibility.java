public class Stage2Visibility {

    static class Worker extends Thread {
        boolean stopped = false;

        @Override
        public void run() {
            while (!stopped) {}
            System.out.println("Worker stopped");
        }
    }

    public static void main(String[] args) throws Exception {
        Worker worker = new Worker();
        worker.start();

        Thread.sleep(1000);

        System.out.println("Main: stop signal");
        worker.stopped = true;

        worker.join();
        System.out.println("Main: end");
    }
}
