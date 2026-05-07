package race;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * AtomicInteger 도입
 * (락 획득/해제 비용 X)
 */

public class BatchProcessorAtomic implements Counter {

    private AtomicInteger processedCount = new AtomicInteger(0);

    public void process() {
        processedCount.incrementAndGet(); // read → modify → write 중 다른 스레드와 충돌 가능
    }

    public int getProcessedCount() {
        return processedCount.get();
    }
}
