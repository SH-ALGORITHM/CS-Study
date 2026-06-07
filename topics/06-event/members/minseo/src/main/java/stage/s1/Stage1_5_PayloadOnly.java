package stage.s1;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
@EnableAutoConfiguration
public class Stage1_5_PayloadOnly {

    public record OrderPlacedEvent(Long orderId, String name) {}

    @Bean
    public Publisher publisher(ApplicationEventPublisher p) {
        return new Publisher(p);
    }

    @Bean public RecordListener recordListener() { return new RecordListener(); }
    @Bean public StringListener stringListener() { return new StringListener(); }

    public static class Publisher {
        private final ApplicationEventPublisher publisher;
        public Publisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }

        public void publishAll() {
            System.out.println("[publisher] record 발행");
            publisher.publishEvent(new OrderPlacedEvent(1L, "라면"));

            System.out.println("[publisher] String 발행 — payload-only");
            publisher.publishEvent("그냥 문자열");
        }
    }

    public static class RecordListener {
        @EventListener
        public void on(OrderPlacedEvent e) {
            System.out.println("  [record] " + e.orderId() + " / " + e.name());
        }
    }

    public static class StringListener {
        @EventListener
        public void on(String message) {
            System.out.println("  [String] " + message);
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1_5_PayloadOnly.class, args);
        MeasurementLog.title("STAGE 1-5 — payload-only 이벤트 (Spring 4.2+)");
        ctx.getBean(Publisher.class).publishAll();

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · ApplicationEvent 상속 의무 X — record / POJO / String 다 OK");
        System.out.println("  · 내부적으로 PayloadApplicationEvent<T> 로 감싸짐");
        System.out.println("  · 도메인 이벤트는 전용 record 권장 (타입 충돌 회피)");
        ctx.close();
    }
}
