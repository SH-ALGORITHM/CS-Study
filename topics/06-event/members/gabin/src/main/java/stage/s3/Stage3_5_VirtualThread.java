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
 * STAGE 3-5 — Java 21 Virtual Thread + Spring Boot 3.2.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>application.properties 의 <code>spring.threads.virtual.enabled=true</code> 한 줄</li>
 *   <li><b>검증은 Thread.currentThread().isVirtual()</b> — Boot 의 SimpleAsyncTaskExecutor 는 가상 스레드 모드에서도 prefix=task- 유지. 이름만으로 판별 불가</li>
 *   <li>I/O 바운드 블로킹은 캐리어 스레드 점유 안 함 → 풀 사이즈 튜닝 의미 사라짐</li>
 *   <li>self-invocation 함정은 <b>가상 스레드와 무관하게 그대로</b> — 프록시 메커니즘 이슈, 스레드 도구 바뀌어도 동일</li>
 * </ul>
 *
 * <h3>실행 전 application.properties 에 추가</h3>
 * <pre>
 * spring.threads.virtual.enabled=true
 * </pre>
 *
 * <h3>주의</h3>
 * 위에서 명시 <code>@Bean(name="applicationTaskExecutor") ThreadPoolTaskExecutor</code> 를 등록하면
 * 자동 설정을 덮어쓰므로 가상 스레드 안 켜짐. 이 stage 는 명시 executor 빈 없이 자동 설정만 사용.
 */
@Configuration
@EnableAutoConfiguration
@EnableAsync
public class Stage3_5_VirtualThread {

    public record OrderCompletedEvent(Long orderId) {}

    @Bean
    public OrderService orderService(ApplicationEventPublisher publisher) {
        return new OrderService(publisher);
    }

    @Bean public IoBoundListener1 l1() { return new IoBoundListener1(); }
    @Bean public IoBoundListener2 l2() { return new IoBoundListener2(); }
    @Bean public IoBoundListener3 l3() { return new IoBoundListener3(); }

    public static class OrderService {
        private final ApplicationEventPublisher publisher;
        public OrderService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

        public long completeOrder(Long orderId) {
            long t1 = System.nanoTime();
            publisher.publishEvent(new OrderCompletedEvent(orderId));
            long elapsedMs = (System.nanoTime() - t1) / 1_000_000;
            System.out.println("  [publisher] return — total=" + elapsedMs + "ms thread=" + MeasurementLog.thread());
            return elapsedMs;
        }
    }

    private static void ioBoundWork(String label) throws InterruptedException {
        // I/O 바운드 시뮬레이션 (DB / HTTP). 가상 스레드면 캐리어 점유 안 함
        Thread t = Thread.currentThread();
        System.out.println("    [" + label + "] start thread=" + t.getName()
            + " isVirtual=" + t.isVirtual());
        Thread.sleep(200);
        System.out.println("    [" + label + "] end");
    }

    public static class IoBoundListener1 {
        @Async @EventListener
        public void on(OrderCompletedEvent e) throws InterruptedException { ioBoundWork("재고 시스템"); }
    }

    public static class IoBoundListener2 {
        @Async @EventListener
        public void on(OrderCompletedEvent e) throws InterruptedException { ioBoundWork("알림 시스템"); }
    }

    public static class IoBoundListener3 {
        @Async @EventListener
        public void on(OrderCompletedEvent e) throws InterruptedException { ioBoundWork("통계 시스템"); }
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_5_VirtualThread.class, args);

        MeasurementLog.title("STAGE 3-5 — Virtual Thread (Java 21 + Boot 3.2)");

        long elapsedMs = ctx.getBean(OrderService.class).completeOrder(1L);
        Thread.sleep(500);
        MeasurementLog.record(
            "s3-5",
            "Virtual Thread 비동기 리스너 3개 — publisher=" + elapsedMs
                + "ms / isVirtual=true"
        );

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · application.properties: spring.threads.virtual.enabled=true 켜져야 가상 스레드");
        System.out.println("  · 검증은 Thread.currentThread().isVirtual() 로 — 스레드명 prefix 는 그대로 task- 일 수 있음");
        System.out.println("  · I/O 바운드 리스너의 풀 사이즈 튜닝 고민 자체가 사라짐");
        System.out.println("  · self-invocation 함정 (Stage3_3) 은 가상 스레드와 무관 — 프록시 이슈 그대로");
        ctx.close();
    }
}

