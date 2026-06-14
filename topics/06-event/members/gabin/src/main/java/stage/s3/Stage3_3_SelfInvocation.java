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
 * STAGE 3-3 — @Async self-invocation 함정 (5 주차 @Transactional 회수).
 *
 * <h3>세 시나리오 비교</h3>
 * <ol>
 *   <li>this.async() — 같은 클래스 안 호출 → 비동기 X (main 스레드)</li>
 *   <li>주입된 다른 빈의 async() — 정상 비동기 (event-N 스레드)</li>
 *   <li>publishEvent → 다른 클래스 @Async @EventListener — 정상 비동기. <b>가장 6 주차스러운 해결</b></li>
 * </ol>
 *
 * <h3>핵심</h3>
 * @Async 도 프록시 메커니즘 — this 는 원본 객체 → 프록시 우회. 5 주차 @Transactional 함정과 정확히 동일.
 */
@Configuration
@EnableAutoConfiguration
@EnableAsync
public class Stage3_3_SelfInvocation {

    public record OrderCompletedEvent(Long orderId) {}

    @Bean
    public SelfCaller selfCaller() { return new SelfCaller(); }

    @Bean
    public OtherWorker otherWorker() { return new OtherWorker(); }

    @Bean
    public CallerWithEvent callerWithEvent(ApplicationEventPublisher p, OtherWorker o) {
        return new CallerWithEvent(p, o);
    }

    @Bean
    public EventDrivenWorker eventDrivenWorker() { return new EventDrivenWorker(); }

    public static class SelfCaller {
        public void outer() {
            System.out.println("  [outer] thread=" + MeasurementLog.thread());
            this.innerAsync();   // ← this 호출 → 비동기 X
        }

        @Async
        public void innerAsync() {
            System.out.println("  [innerAsync via this] thread=" + MeasurementLog.thread()
                + "  ← 비동기 X (프록시 우회)");
        }
    }

    public static class OtherWorker {
        @Async
        public void doWork(String label) {
            System.out.println("  [" + label + "] thread=" + MeasurementLog.thread());
        }
    }

    public static class CallerWithEvent {
        private final ApplicationEventPublisher publisher;
        private final OtherWorker worker;
        public CallerWithEvent(ApplicationEventPublisher publisher, OtherWorker worker) {
            this.publisher = publisher;
            this.worker = worker;
        }

        public void viaOther() {
            worker.doWork("via other worker");
        }

        public void viaEvent() {
            publisher.publishEvent(new OrderCompletedEvent(300L));
        }
    }

    public static class EventDrivenWorker {
        @Async
        @EventListener
        public void on(OrderCompletedEvent e) {
            System.out.println("  [via @EventListener] thread=" + MeasurementLog.thread()
                + " orderId=" + e.orderId());
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_3_SelfInvocation.class, args);
        SelfCaller self = ctx.getBean(SelfCaller.class);
        CallerWithEvent caller = ctx.getBean(CallerWithEvent.class);

        MeasurementLog.title("STAGE 3-3 — @Async self-invocation");

        MeasurementLog.section("(1) this.innerAsync()");
        self.outer();

        Thread.sleep(100);
        MeasurementLog.section("(2) 주입된 다른 빈의 async()");
        caller.viaOther();

        Thread.sleep(100);
        MeasurementLog.section("(3) publishEvent → @EventListener");
        caller.viaEvent();
        Thread.sleep(100);

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · this 호출 → 비동기 X (main). 5 주차 @Transactional 함정과 동일");
        System.out.println("  · 클래스 분리 → 비동기 O");
        System.out.println("  · publishEvent → 다른 클래스 @EventListener 가 자연스러운 우회");
        MeasurementLog.record(
            "s3-3",
            "this.@Async=self 호출로 main / 다른 Bean과 Event 리스너=비동기"
        );
        ctx.close();
    }
}

