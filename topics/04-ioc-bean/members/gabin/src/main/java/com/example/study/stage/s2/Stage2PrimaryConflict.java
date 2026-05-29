package com.example.study.stage.s2;

import com.example.study.MeasurementLog;
import com.example.study.domain.DiscountPolicy;
import com.example.study.domain.FixedDiscount;
import com.example.study.domain.PercentDiscount;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * STAGE 2-5: @Primary 기본값과 @Qualifier 명시 지정 우선순위 비교.
 */
public class Stage2PrimaryConflict {

    private static final int ORDER_AMOUNT = 50_000;

    @Configuration
    static class AppConfig {
        @Bean
        @Primary
        public DiscountPolicy primaryPercentDiscount() {
            return new PercentDiscount();
        }

        @Bean
        public DiscountPolicy fixedDiscountPolicy() {
            return new FixedDiscount();
        }

        @Bean
        public DefaultOrderService defaultOrderService(DiscountPolicy discountPolicy) {
            return new DefaultOrderService(discountPolicy);
        }

        @Bean
        public QualifiedOrderService qualifiedOrderService(
            @Qualifier("fixedDiscountPolicy") DiscountPolicy discountPolicy
        ) {
            return new QualifiedOrderService(discountPolicy);
        }
    }

    static class DefaultOrderService {
        private final DiscountPolicy discountPolicy;

        DefaultOrderService(DiscountPolicy discountPolicy) {
            this.discountPolicy = discountPolicy;
        }

        int finalPrice(int orderAmount) {
            return orderAmount - discountPolicy.discount(orderAmount);
        }
    }

    static class QualifiedOrderService {
        private final DiscountPolicy discountPolicy;

        QualifiedOrderService(DiscountPolicy discountPolicy) {
            this.discountPolicy = discountPolicy;
        }

        int finalPrice(int orderAmount) {
            return orderAmount - discountPolicy.discount(orderAmount);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            DefaultOrderService defaultService = ctx.getBean(DefaultOrderService.class);
            QualifiedOrderService qualifiedService = ctx.getBean(QualifiedOrderService.class);

            int primaryPrice = defaultService.finalPrice(ORDER_AMOUNT);
            int qualifierPrice = qualifiedService.finalPrice(ORDER_AMOUNT);

            System.out.println("=== STAGE 2-5: @Primary vs @Qualifier ===");
            System.out.println("@Primary selected final price: " + primaryPrice);
            System.out.println("@Qualifier selected final price: " + qualifierPrice);

            MeasurementLog.save(
                "s2-5",
                "@Primary vs @Qualifier",
                String.join(System.lineSeparator(),
                    "",
                    "  주문 금액: " + ORDER_AMOUNT,
                    "  - @Primary 기본 선택 최종 금액: " + primaryPrice,
                    "  - @Qualifier 명시 선택 최종 금액: " + qualifierPrice,
                    "",
                    "  관찰",
                    "  - @Primary는 같은 타입 Bean이 여러 개일 때 기본 후보를 지정한다.",
                    "  - @Qualifier가 있으면 @Primary보다 @Qualifier가 우선한다.",
                    "  - @Primary는 기본값, @Qualifier는 명시 선택에 가깝다."
                )
            );
        }
    }
}
