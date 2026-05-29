package domain;

import java.math.BigDecimal;

/**
 * 결제 PG 인터페이스 — 다형성 학습 베이스.
 *
 * 구현체 3 개 (Toss / Kakao / Naver) 가 모두 이 인터페이스를 구현.
 * PaymentService 는 @Qualifier 또는 Map<String, PaymentGateway> 로 주입받음.
 */
public interface PaymentGateway {

    void pay(long userId, BigDecimal amount);
}
