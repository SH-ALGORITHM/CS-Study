package stage.s1;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

class LegacyDemoEvent extends ApplicationEvent {
    private final String message;

    LegacyDemoEvent(Object source, String message) {
        super(source);                  // ApplicationEvent는 source(발행 주체) 필수
        this.message = message;
    }

    String message() { return message; }
}

@Service
class LegacyDemoPublisher {
    private final ApplicationEventPublisher publisher;

    LegacyDemoPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    void publish() {
        System.out.println("[publisher] publish");
        publisher.publishEvent(new LegacyDemoEvent(this, "hi"));
        System.out.println("[publisher] return");
    }
}

// (A) 옛 방식 — ApplicationListener<E> 인터페이스 직접 구현
@Component
class OldStyleListener implements ApplicationListener<LegacyDemoEvent> {
    @Override
    public void onApplicationEvent(LegacyDemoEvent event) {
        System.out.println("[OldStyle] " + event.message());
    }
}

// (B) 비교용 — 같은 이벤트를 @EventListener 로 받기 (완성 제공)
@Component
class AnnotationStyleListener {
    @EventListener
    void on(LegacyDemoEvent e) {
        System.out.println("[Annotation] " + e.message());
    }
}

@SpringBootApplication
public class Stage1_4_OldStyle {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage1_4_OldStyle.class, args);
        ctx.getBean(LegacyDemoPublisher.class).publish();

       MeasurementLog.save("s1-4", "ApplicationListener<E> vs @EventListener — 동작 동일(같은 메커니즘), 단\n" +
           "  인터페이스는 클래스당 이벤트 1개 고정");
    }
}
