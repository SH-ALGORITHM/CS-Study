package race;

/**
 * synchronized 메서드 도입
 */

public class BatchProcessorSync implements Counter {

    private int processedCount = 0;

    public synchronized void process() {
        processedCount++;
    }

    public synchronized int getProcessedCount() {
        return processedCount;
    }
}
