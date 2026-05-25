package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Toss 결제 PG 구현체.
 *
 * Bean 이름 = "toss" (@Component 의 value).
 * @Primary 로 기본 구현체 지정 — @Qualifier 없을 때 자동 선택.
 */
@Component("toss")
@Primary
public class TossPayment implements PaymentGateway {

    public TossPayment() {
        System.out.println("[TossPayment] 생성자 호출");
    }

    @PostConstruct
    public void init() {
        System.out.println("[TossPayment] @PostConstruct");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[TossPayment] @PreDestroy");
    }

    @Override
    public void pay(long userId, BigDecimal amount) {
        System.out.println("[Toss] userId=" + userId + " amount=" + amount + " 결제 완료");
    }
}
