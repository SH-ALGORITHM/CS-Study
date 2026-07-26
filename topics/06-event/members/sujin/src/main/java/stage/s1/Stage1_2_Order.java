package stage.s1;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

// 1-2 전용 이벤트 — 1-1 의 HelloEvent 와 분리해서 리스너 교차 발화 방지.
// (payload 타입이 곧 라우팅 키 — 1-5 에서 다시 나올 개념)
record OrderDemoEvent(String message) {}

@Service
class OrderDemoPublisher {
    private final ApplicationEventPublisher publisher;

    OrderDemoPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    void publish() {
        System.out.println("[publisher] publish");
        publisher.publishEvent(new OrderDemoEvent("hi"));
        System.out.println("[publisher] return");
    }
}

@Component
class ListenerA {
    @EventListener
    @Order(2)
    void on(OrderDemoEvent e) {
        System.out.println("[A] " + e.message());
    }
}

@Component
class ListenerB {
    @EventListener
    @Order(1)
    void on(OrderDemoEvent e) {
        System.out.println("[B] " + e.message());
    }
}

@Component
class ListenerC {
    @EventListener
    @Order(0)
    void on(OrderDemoEvent e) {
        System.out.println("[C] " + e.message());
    }
}

@SpringBootApplication
public class Stage1_2_Order {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage1_2_Order.class, args);
        ctx.getBean(OrderDemoPublisher.class).publish();

        MeasurementLog.save("s1-2", "@Order로 리스너 호출 순서 제어 확인 (작은 값 먼저)");
    }
}
