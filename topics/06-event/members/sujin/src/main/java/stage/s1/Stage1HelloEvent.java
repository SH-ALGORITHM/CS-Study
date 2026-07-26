package stage.s1;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

// (1) 이벤트 — record 로 충분 (Spring 4.2+). 완성본.
record HelloEvent(String message) {}

// (2) Publisher — ApplicationEventPublisher 주입
@Service
class HelloService {
    private final ApplicationEventPublisher publisher;

    HelloService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    void sayHello(String name) {
        System.out.println("[publisher] sayHello — " + name
                + " / thread=" + Thread.currentThread().getName());

        publisher.publishEvent(new HelloEvent("hello " + name));

        System.out.println("[publisher] return"
                + " / thread=" + Thread.currentThread().getName());
    }
}

// (3) Listener — @EventListener 메서드 1 개
@Component
class HelloListener {

    @EventListener
    void onHello(HelloEvent event) {
        String message = event.message();

        System.out.println("[listener] received — " + message + " / threads=" + Thread.currentThread().getName());
    }
}

// (4) main
@SpringBootApplication
public class Stage1HelloEvent {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage1HelloEvent.class, args);
        ctx.getBean(HelloService.class).sayHello("world");

        MeasurementLog.save("s1-1", "HelloEvent 발행 → 동기 리스너 호출 순서 println 확인");

    }
}
