package stage.s1;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

// payload-only — ApplicationEvent 상속 없는 그냥 record (Spring 4.2+)
record PayloadDemoEvent(String message) {}

@Service
class PayloadDemoPublisher {
    private final ApplicationEventPublisher publisher;

    PayloadDemoPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    void publishAll() {
        System.out.println("[publisher] publish String");
        publisher.publishEvent("그냥 발행");

        System.out.println("[publisher] publish record");
        publisher.publishEvent(new PayloadDemoEvent("record 발행"));

        System.out.println("[publisher] return");
    }
}

// (A) String 을 직접 받는 리스너 — payload 타입이 String
@Component
class StringPayloadListener {
    @EventListener
    void on(String message) {
        System.out.println("[String payload] " + message);
    }
}

// (B) record 를 받는 리스너
@Component
class RecordPayloadListener {
    @EventListener
    void on(PayloadDemoEvent e) {
        System.out.println("[Record payload] " + e.message());
    }
}

@SpringBootApplication
public class Stage1_5_PayloadOnly {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage1_5_PayloadOnly.class, args);
        ctx.getBean(PayloadDemoPublisher.class).publishAll();

        MeasurementLog.save("s1-5", "payload-only — ApplicationEvent 상속없이 String/record 발행, 타입으로 매칭 (내부 PayloadApplicationEvent 래핑)");
    }
}
