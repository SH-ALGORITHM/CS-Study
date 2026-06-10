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

/**
 * STAGE 3-3 — @Async self-invocation 함정 (5주차 @Transactional 복습)
 * 
 * [시나리오]
 * 재고가 부족할 때 '내부적으로' 비동기 처리를 하려고 시도합니다.
 * 1. 내부 메서드 직접 호출 (this) → 프록시 우회로 인해 비동기 작동 안 함
 * 2. 외부 빈 메서드 호출 → 정상 비동기
 * 3. 이벤트를 통한 호출 → 정상 비동기 (6주차 권장 방식)
 */
@Configuration
@EnableAutoConfiguration
@EnableAsync
public class Stage3_3_SelfInvocation {

    public record StockLowEvent(Long id) {}

    @Bean
    public InventoryService inventoryService(ApplicationEventPublisher p, OtherWorker o) {
        return new InventoryService(p, o);
    }

    @Bean
    public OtherWorker otherWorker() { return new OtherWorker(); }

    @Bean
    public AsyncEventListener asyncEventListener() { return new AsyncEventListener(); }

    public static class InventoryService {
        private final ApplicationEventPublisher publisher;
        private final OtherWorker otherWorker;

        public InventoryService(ApplicationEventPublisher publisher, OtherWorker otherWorker) {
            this.publisher = publisher;
            this.otherWorker = otherWorker;
        }

        /** (1) 함정: 같은 클래스 내의 @Async 메서드 호출 */
        public void checkWithSelfInvocation(Long id) {
            System.out.println("  [Service] checkWithSelfInvocation id=" + id + " " + MeasurementLog.thread());
            this.internalAsyncWork(); // ← 프록시를 거치지 않고 직접 호출됨
        }

        @Async
        public void internalAsyncWork() {
            System.out.println("  [Internal] 비동기 작업 시도... " + MeasurementLog.thread() + " ← 함정! (비동기 X)");
        }

        /** (2) 해결: 외부 주입된 빈의 @Async 메서드 호출 */
        public void checkWithOtherWorker(Long id) {
            System.out.println("  [Service] checkWithOtherWorker id=" + id + " " + MeasurementLog.thread());
            otherWorker.doWork("ExternalWorker");
        }

        /** (3) 권장: 이벤트를 발행하여 외부 @Async 리스너에서 처리 */
        public void checkWithEvent(Long id) {
            System.out.println("  [Service] checkWithEvent id=" + id + " " + MeasurementLog.thread());
            publisher.publishEvent(new StockLowEvent(id));
        }
    }

    public static class OtherWorker {
        @Async
        public void doWork(String label) {
            System.out.println("    [" + label + "] 비동기 작업 성공! " + MeasurementLog.thread());
        }
    }

    public static class AsyncEventListener {
        @Async
        @EventListener
        public void on(StockLowEvent e) {
            System.out.println("    [EventListener] 비동기 리스너 성공! " + MeasurementLog.thread());
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_3_SelfInvocation.class, args);
        InventoryService service = ctx.getBean(InventoryService.class);

        MeasurementLog.title("STAGE 3-3 — @Async Self-Invocation 함정 (재고 도메인)");

        MeasurementLog.section("(1) 클래스 내부 @Async 호출 (this)");
        service.checkWithSelfInvocation(1L);
        Thread.sleep(100);

        MeasurementLog.section("(2) 주입된 다른 빈의 @Async 호출");
        service.checkWithOtherWorker(2L);
        Thread.sleep(100);

        MeasurementLog.section("(3) publishEvent -> @Async @EventListener");
        service.checkWithEvent(3L);
        Thread.sleep(100);

        System.out.println("\n[학습 포인트]");
        System.out.println("  · @Async도 프록시 기반이므로 같은 클래스 내 호출(this)은 비동기로 동작하지 않음");
        System.out.println("  · 5주차 @Transactional 함정과 기술적으로 동일한 원인");
        System.out.println("  · 이벤트를 사용하면 자연스럽게 클래스가 분리되어 비동기 우회가 해결됨");

        ctx.close();
    }
}
