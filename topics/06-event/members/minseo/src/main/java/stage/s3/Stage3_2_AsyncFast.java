package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * STAGE 3-2 — @EnableAsync + @Async → 별 스레드. (재고 도메인)
 * : 비동기 처리를 통해 리스너가 publisher(InventoryService)를 블록하지 않도록 개선합니다.
 */
@Configuration
@EnableAutoConfiguration
@EnableAsync // ★ 비동기 기능 활성화
public class Stage3_2_AsyncFast {

    public record StockLowEvent(Long id, int currentStock) {}

    /**
     * @Async 가 사용할 스레드풀 설정
     * - core: 4 (기본 유지 스레드)
     * - max: 8 (부하 시 최대 확장)
     * - queue: 100 (큐가 가득 차야 max까지 확장됨)
     */
    @Bean(name = "applicationTaskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-");
        executor.initialize();
        return executor;
    }

    @Bean
    public InventoryService inventoryService(ApplicationEventPublisher publisher) {
        return new InventoryService(publisher);
    }

    @Bean public AsyncNotificationListener l1() { return new AsyncNotificationListener(); }
    @Bean public AsyncStatisticsListener l2() { return new AsyncStatisticsListener(); }
    @Bean public AsyncErpSyncListener l3() { return new AsyncErpSyncListener(); }

    public static class InventoryService {
        private final ApplicationEventPublisher publisher;
        public InventoryService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

        public void checkStock(Long id, int stock) {
            System.out.println("[Service] 재고 확인 — id=" + id + " stock=" + stock + " " + MeasurementLog.thread());
            
            long t1 = System.nanoTime();
            if (stock < 10) {
                // 비동기 리스너인 경우, 이 호출은 즉시 리턴됩니다.
                publisher.publishEvent(new StockLowEvent(id, stock));
            }
            long elapsedMs = (System.nanoTime() - t1) / 1_000_000;
            
            System.out.println("[Service] 리턴 완료 — 총 소요시간=" + elapsedMs + "ms " + MeasurementLog.thread());
        }
    }

    private static void slowWork(String label) throws InterruptedException {
        System.out.println("    [" + label + "] 처리 시작... " + MeasurementLog.thread());
        Thread.sleep(200);
        System.out.println("    [" + label + "] 완료");
    }

    public static class AsyncNotificationListener {
        @Async // ★ 별도 스레드에서 실행
        @EventListener
        public void on(StockLowEvent e) throws InterruptedException {
            slowWork("Notification");
        }
    }

    public static class AsyncStatisticsListener {
        @Async
        @EventListener
        public void on(StockLowEvent e) throws InterruptedException {
            slowWork("Statistics");
        }
    }

    public static class AsyncErpSyncListener {
        @Async
        @EventListener
        public void on(StockLowEvent e) throws InterruptedException {
            slowWork("ERP Sync");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_2_AsyncFast.class, args);
        InventoryService service = ctx.getBean(InventoryService.class);

        MeasurementLog.title("STAGE 3-2 — @Async 비동기 리스너 도입 (재고 도메인)");
        
        service.checkStock(1L, 5);

        // 비동기 로그 확인을 위해 메인 스레드 잠시 대기
        Thread.sleep(500);

        System.out.println("\n[관찰 포인트]");
        System.out.println("  1. 서비스 로직의 리턴 시간이 0~5ms 내외로 비약적으로 단축되었는가?");
        System.out.println("  2. 리스너들의 로그에 찍힌 스레드 이름이 'event-1', 'event-2' 등으로 바뀌었는가?");
        System.out.println("  3. 리스너 3개가 거의 동시에(병렬로) 처리를 시작하는가?");

        ctx.close();
    }
}
