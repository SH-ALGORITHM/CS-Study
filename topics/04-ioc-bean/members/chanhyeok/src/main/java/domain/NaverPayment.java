package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Naver 결제 PG 구현체. Bean 이름 = "naver".
 */
@Component("naver")
public class NaverPayment implements PaymentGateway {

    public NaverPayment() {
        System.out.println("[NaverPayment] 생성자 호출");
    }

    @PostConstruct
    public void init() {
        System.out.println("[NaverPayment] @PostConstruct");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[NaverPayment] @PreDestroy");
    }

    @Override
    public void pay(long userId, BigDecimal amount) {
        System.out.println("[Naver] userId=" + userId + " amount=" + amount + " 결제 완료");
    }
}
