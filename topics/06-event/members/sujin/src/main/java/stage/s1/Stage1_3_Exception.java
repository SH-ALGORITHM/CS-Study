package stage.s1;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

record ExceptionDemoEvent(String message) {}

@Service
class ExceptionDemoPublisher {
    private final ApplicationEventPublisher publisher;

    ExceptionDemoPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    void publish() {
        System.out.println("[publisher] publish");
        publisher.publishEvent(new ExceptionDemoEvent("hi"));
        System.out.println("[publisher] return");   // ← 이 줄이 찍히는지 관찰 포인트
    }
}

@Component
class FirstListener {
    @EventListener
    @Order(1)
    void on(ExceptionDemoEvent e) {
        System.out.println("[L1 order=1] received");
    }
}

@Component
class FailingListener {
    @EventListener
    @Order(2)
    void on(ExceptionDemoEvent e) {
        System.out.println("[L2 order=2] received — throwing");

        throw new RuntimeException("일부러 실패");
    }
}

@Component
class ThirdListener {
    @EventListener
    @Order(3)
    void on(ExceptionDemoEvent e) {
        System.out.println("[L3 order=3] received");   // ← 이게 찍히나? 관찰 포인트
    }
}

@SpringBootApplication
public class Stage1_3_Exception {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage1_3_Exception.class, args);

        try {
            ctx.getBean(ExceptionDemoPublisher.class).publish();
        } catch (Exception e) {
            System.out.println("[main] caught — " + e.getMessage());   // ← 전파됐나? 관찰 포인트
        }

        MeasurementLog.save("s1-3", "동기 기준동작: 예외 전파 O, 체인 중단 O — STAGE3 @Async 면 전파 X 예정");
    }
}
