package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Kakao 결제 PG 구현체. Bean 이름 = "kakao".
 */
@Component("kakao")
public class KakaoPayment implements PaymentGateway {

    public KakaoPayment() {
        System.out.println("[KakaoPayment] 생성자 호출");
    }

    @PostConstruct
    public void init() {
        System.out.println("[KakaoPayment] @PostConstruct");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[KakaoPayment] @PreDestroy");
    }

    @Override
    public void pay(long userId, BigDecimal amount) {
        System.out.println("[Kakao] userId=" + userId + " amount=" + amount + " 결제 완료");
    }
}
