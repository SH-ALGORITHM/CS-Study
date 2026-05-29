package domain;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 사용자가 선택한 PG 로 분기하는 Dispatcher — Strategy 패턴 + OCP 정석.
 *
 * <h3>Map<String, T> 자동 주입</h3>
 * Spring 이 PaymentGateway 타입의 모든 Bean 을 Map 으로 자동 주입.
 * 키 = Bean 이름 = @Component value ("toss" / "kakao" / "naver").
 *
 * <h3>OCP 달성</h3>
 * 새 PG 추가 = @Component 클래스 1 개만 추가. PaymentDispatcher 코드 수정 X.
 */
@Service
public class PaymentDispatcher {

    private final Map<String, PaymentGateway> gateways;

    public PaymentDispatcher(Map<String, PaymentGateway> gateways) {
        this.gateways = gateways;
        System.out.println("[PaymentDispatcher] 주입받은 PG 키: " + gateways.keySet());
    }

    @PostConstruct
    public void init() {
        System.out.println("[PaymentDispatcher] @PostConstruct");
    }

    public void pay(String method, long userId, BigDecimal amount) {
        PaymentGateway gateway = gateways.get(method);
        if (gateway == null) {
            System.out.println("⚠️ method=\"" + method + "\" 에 해당하는 PG 없음. "
                + "등록된 키: " + gateways.keySet());
            return;
        }
        gateway.pay(userId, amount);
    }
}
