package domain;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 결제 상위 서비스 — 계층 분리 + 생성자 주입 + @Qualifier 학습 베이스.
 *
 * 생성자 주입으로 PaymentGateway 받음. @Qualifier("toss") 로 3 개 구현체 중 TossPayment 지정.
 *
 * <h3>학습 포인트</h3>
 * <ul>
 *   <li>final 필드 → 생성자 주입만 가능</li>
 *   <li>@Qualifier 가 @Primary 보다 우선</li>
 *   <li>주입된 구현체를 본인이 모르고 받음 — 다형성의 핵심</li>
 * </ul>
 */
@Service
public class PaymentService {

    private final PaymentGateway gateway;

    public PaymentService(@Qualifier("toss") PaymentGateway gateway) {
        System.out.println("[PaymentService] 생성자 — 주입된 gateway: "
            + gateway.getClass().getSimpleName());
        this.gateway = gateway;
    }

    @PostConstruct
    public void init() {
        System.out.println("[PaymentService] @PostConstruct");
    }

    public void pay(long userId, BigDecimal amount) {
        gateway.pay(userId, amount);
    }
}
