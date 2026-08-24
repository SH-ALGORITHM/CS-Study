package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * STAGE 3-1 — 동기 리스너의 한계 (Sync Slow)
 * : 리스너가 느리면 publisher(InventoryService)도 그만큼 블록됨을 재현합니다.
 */
@Configuration
@EnableAutoConfiguration
public class Stage3_1_SyncSlow {

    public record StockLowEvent(Long id, int currentStock) {}

    @Bean
    public InventoryService inventoryService(ApplicationEventPublisher publisher) {
        return new InventoryService(publisher);
    }

    @Bean public NotificationListener l1() { return new NotificationListener(); }
    @Bean public StatisticsListener l2() { return new StatisticsListener(); }
    @Bean public ErpSyncListener l3() { return new ErpSyncListener(); }

    public static class InventoryService {
        private final ApplicationEventPublisher publisher;
        public InventoryService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

        public void checkStock(Long id, int stock) {
            System.out.println("[Service] 재고 확인 — id=" + id + " stock=" + stock);
            
            long t1 = System.nanoTime();
            if (stock < 10) {
                publisher.publishEvent(new StockLowEvent(id, stock));
            }
            long elapsedMs = (System.nanoTime() - t1) / 1_000_000;
            
            System.out.println("[Service] 리턴 완료 — 총 소요시간=" + elapsedMs + "ms " + MeasurementLog.thread());
        }
    }

    private static void slowWork(String label) throws InterruptedException {
        System.out.println("    [" + label + "] 처리 중... " + MeasurementLog.thread());
        Thread.sleep(200);
        System.out.println("    [" + label + "] 완료");
    }

    public static class NotificationListener {
        @EventListener
        public void on(StockLowEvent e) throws InterruptedException {
            slowWork("Notification");
        }
    }

    public static class StatisticsListener {
        @EventListener
        public void on(StockLowEvent e) throws InterruptedException {
            slowWork("Statistics");
        }
    }

    public static class ErpSyncListener {
        @EventListener
        public void on(StockLowEvent e) throws InterruptedException {
            slowWork("ERP Sync");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_1_SyncSlow.class, args);
        InventoryService service = ctx.getBean(InventoryService.class);

        MeasurementLog.title("STAGE 3-1 — 동기 리스너의 블록킹 현상 (재고 도메인)");
        service.checkStock(1L, 5);

        ctx.close();
    }
}
