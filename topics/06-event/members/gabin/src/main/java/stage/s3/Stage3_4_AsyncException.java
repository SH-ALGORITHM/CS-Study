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
 * STAGE 3-4 — @Async void 메서드 예외가 어디로 가나.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>void 메서드 — 예외가 호출자에 전파 X. 그냥 사라질 위험</li>
 *   <li>AsyncConfigurer 구현 + AsyncUncaughtExceptionHandler 등록 → void 예외도 잡음</li>
 *   <li>Future&lt;T&gt; 반환 시 — future.get() 호출 시 ExecutionException 으로 받음 (이 stage 에서는 void 만)</li>
 * </ul>
 */
@Configuration
@EnableAutoConfiguration
@EnableAsync
public class Stage3_4_AsyncException implements AsyncConfigurer {

    public record OrderCompletedEvent(Long orderId) {}

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new MyAsyncExceptionHandler();
    }

    @Bean
    public FailingService failingService() { return new FailingService(); }

    @Bean
    public OrderService orderService(ApplicationEventPublisher publisher) {
        return new OrderService(publisher);
    }

    public static class OrderService {
        private final ApplicationEventPublisher publisher;

        public OrderService(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        public void completeOrder(Long orderId) {
            publisher.publishEvent(new OrderCompletedEvent(orderId));
        }
    }

    public static class FailingService {
        @Async
        @EventListener
        public void sendCompletionNotification(OrderCompletedEvent event) {
            System.out.println("  [sendCompletionNotification] orderId=" + event.orderId()
                + " thread=" + MeasurementLog.thread() + " — throwing");
            throw new RuntimeException("주문완료 알림 전송 실패");
        }
    }

    public static class MyAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            System.err.println("  [ASYNC ERROR] method=" + method.getName()
                + " ex=" + ex.getMessage()
                + " thread=" + MeasurementLog.thread());
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_4_AsyncException.class, args);

        MeasurementLog.title("STAGE 3-4 — @Async void 예외 → AsyncUncaughtExceptionHandler");

        try {
            ctx.getBean(OrderService.class).completeOrder(400L);
            System.out.println("  [caller] return (예외 전파 X — void 라서 사라짐)");
        } catch (RuntimeException ex) {
            System.out.println("  [caller] caught — " + ex.getMessage() + "  (이건 안 나옴)");
        }

        Thread.sleep(200);      // 비동기 예외 핸들러 실행 대기

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · void 메서드 — 호출자 [caller] caught 안 됨. ExceptionHandler 가 받음");
        System.out.println("  · AsyncConfigurer.getAsyncUncaughtExceptionHandler() 안 짜면 예외가 사라질 위험");
        System.out.println("  · Future<T> 반환 시 — future.get() 호출 시 ExecutionException");
        MeasurementLog.record(
            "s3-4",
            "@Async void 예외는 caller에 전파 X / AsyncUncaughtExceptionHandler가 수신"
        );
        ctx.close();
    }
}

