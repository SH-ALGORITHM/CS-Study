package stage.s3;

import infra.MeasurementLog;
import java.lang.reflect.Method;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * STAGE 3-4 — @Async void 메서드 예외 처리 (재고 도메인)
 * : 비동기 리스너(void 반환)에서 발생한 예외가 어떻게 처리되는지 확인합니다.
 */
@Configuration
@EnableAutoConfiguration
@EnableAsync
public class Stage3_4_AsyncException implements AsyncConfigurer {

    /**
     * ★ 핵심: 비동기 작업 중 발생하는 예외를 잡기 위한 핸들러 등록
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new InventoryAsyncExceptionHandler();
    }

    public record StockLowEvent(Long id) {}

    @Bean
    public InventoryService inventoryService(ApplicationEventPublisher publisher) {
        return new InventoryService(publisher);
    }

    @Bean
    public FailingErpSyncListener failingListener() {
        return new FailingErpSyncListener();
    }

    public static class InventoryService {
        private final ApplicationEventPublisher publisher;
        public InventoryService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

        public void processStock(Long id) {
            System.out.println("[Service] 재고 처리 중... " + MeasurementLog.thread());
            // 비동기 예외가 발생할 이벤트를 발행합니다.
            publisher.publishEvent(new StockLowEvent(id));
            System.out.println("[Service] 호출 완료 (예외 전파 안 됨)");
        }
    }

    public static class FailingErpSyncListener {
        @Async
        @EventListener
        public void on(StockLowEvent e) {
            System.out.println("    [ERP Listener] 비동기 동기화 시도... " + MeasurementLog.thread());
            // 의도적인 런타임 예외 발생
            throw new RuntimeException("ERP 시스템 서버 응답 없음!");
        }
    }

    /**
     * 비동기 예외 전용 핸들러
     */
    public static class InventoryAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            System.err.println("  [ASYNC ERROR LOG]");
            System.err.println("    - Method: " + method.getName());
            System.err.println("    - Message: " + ex.getMessage());
            System.err.println("    - Thread: " + MeasurementLog.thread());
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_4_AsyncException.class, args);
        InventoryService service = ctx.getBean(InventoryService.class);

        MeasurementLog.title("STAGE 3-4 — 비동기 void 예외 핸들링 (재고 도메인)");

        try {
            service.processStock(1L);
        } catch (Exception e) {
            // 이 catch 문은 작동하지 않습니다. 비동기 예외는 이미 다른 스레드로 분기되었기 때문입니다.
            System.out.println("[Main] 이곳에서는 예외를 잡을 수 없습니다: " + e.getMessage());
        }

        // 비동기 핸들러가 로그를 남길 때까지 잠시 대기
        Thread.sleep(500);

        System.out.println("\n[학습 포인트]");
        System.out.println("  1. @Async void 메서드는 예외가 발생해도 호출자(Service)에게 전파되지 않습니다.");
        System.out.println("  2. 핸들러를 등록하지 않으면 예외가 로그 없이 사라질 수 있어 위험합니다.");
        System.err.println("  3. AsyncConfigurer를 구현하여 전역 비동기 예외 핸들러를 설정하는 것이 안전합니다.");

        ctx.close();
    }
}
