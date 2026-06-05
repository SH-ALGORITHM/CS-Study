package stage.s1;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * STAGE 1-4 — ApplicationListener 인터페이스 직접 구현 vs @EventListener.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>둘 다 동일하게 호출됨 — 내부 메커니즘이 같다 (ApplicationListenerMethodAdapter 변환)</li>
 *   <li>ApplicationListener&lt;E extends ApplicationEvent&gt; — Spring 6 에서 제네릭이 ApplicationEvent 로 제한</li>
 *   <li>payload-only (record) 를 받으려면 @EventListener 가 필수 (Stage1_5 참고)</li>
 *   <li>@EventListener = 한 클래스에 여러 메서드 / 여러 이벤트 가능 → 권장</li>
 * </ul>
 */
@Configuration
@EnableAutoConfiguration
public class Stage1_4_OldStyleListener {

    /** 옛 방식 호환용 — ApplicationEvent 상속. */
    public static class HelloEvent extends ApplicationEvent {
        private final String message;
        public HelloEvent(Object source, String message) {
            super(source);
            this.message = message;
        }
        public String message() { return message; }
    }

    @Bean
    public HelloService helloService(ApplicationEventPublisher publisher) {
        return new HelloService(publisher);
    }

    @Bean public NewStyleListener newStyle() { return new NewStyleListener(); }
    @Bean public OldStyleListener oldStyle() { return new OldStyleListener(); }

    public static class HelloService {
        private final ApplicationEventPublisher publisher;
        public HelloService(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }
        public void sayHello(String name) {
            publisher.publishEvent(new HelloEvent(this, "hello " + name));
        }
    }

    /** 새 방식 — @EventListener 메서드 */
    public static class NewStyleListener {
        @EventListener
        public void onHello(HelloEvent event) {
            System.out.println("  [@EventListener] " + event.message());
        }
    }

    /** 옛 방식 — ApplicationListener 인터페이스 직접 구현 (E extends ApplicationEvent 제약) */
    public static class OldStyleListener implements ApplicationListener<HelloEvent> {
        @Override
        public void onApplicationEvent(HelloEvent event) {
            System.out.println("  [ApplicationListener] " + event.message());
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1_4_OldStyleListener.class, args);
        MeasurementLog.title("STAGE 1-4 — ApplicationListener 인터페이스 vs @EventListener");
        ctx.getBean(HelloService.class).sayHello("world");

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · 둘 다 호출됨 — 내부적으로 같은 ApplicationListener 메커니즘");
        System.out.println("  · @EventListener 가 권장 (여러 이벤트 / condition / @Order 메서드별)");
        ctx.close();
    }
}
