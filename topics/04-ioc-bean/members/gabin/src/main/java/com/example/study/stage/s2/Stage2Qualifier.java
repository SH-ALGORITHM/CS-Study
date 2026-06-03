package com.example.study.stage.s2;

import com.example.study.MeasurementLog;
import com.example.study.domain.DiscountPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 2-4: DiscountPolicy 다중 구현체 중 @Qualifier로 하나를 명시 주입.
 */
public class Stage2Qualifier {

    private static final int ORDER_AMOUNT = 50_000;

    @Configuration
    @ComponentScan("com.example.study.domain")
    static class AppConfig {
        @Bean
        public PercentOrderService percentOrderService(
            @Qualifier("percentDiscount") DiscountPolicy discountPolicy
        ) {
            return new PercentOrderService(discountPolicy);
        }

        @Bean
        public FixedOrderService fixedOrderService(
            @Qualifier("fixedDiscount") DiscountPolicy discountPolicy
        ) {
            return new FixedOrderService(discountPolicy);
        }

        static class PercentOrderService {
            private final DiscountPolicy discountPolicy;

            PercentOrderService(DiscountPolicy discountPolicy) {
                this.discountPolicy = discountPolicy;
            }

            int finalPrice(int orderAmount) {
                return orderAmount - discountPolicy.discount(orderAmount);
            }
        }

        static class FixedOrderService {
            private final DiscountPolicy discountPolicy;

            FixedOrderService(DiscountPolicy discountPolicy) {
                this.discountPolicy = discountPolicy;
            }

            int finalPrice(int orderAmount) {
                return orderAmount - discountPolicy.discount(orderAmount);
            }
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            AppConfig.PercentOrderService percent = ctx.getBean(AppConfig.PercentOrderService.class);
            AppConfig.FixedOrderService fixed = ctx.getBean(AppConfig.FixedOrderService.class);

            int percentPrice = percent.finalPrice(ORDER_AMOUNT);
            int fixedPrice = fixed.finalPrice(ORDER_AMOUNT);

            System.out.println("=== STAGE 2-4: @Qualifier ===");
            System.out.println("percentDiscount final price: " + percentPrice);
            System.out.println("fixedDiscount final price: " + fixedPrice);

            MeasurementLog.save(
                "s2-4",
                "@Qualifier discount policy",
                String.join(System.lineSeparator(),
                    "",
                    "  주문 금액: " + ORDER_AMOUNT,
                    "  - @Qualifier(\"percentDiscount\") 최종 금액: " + percentPrice,
                    "  - @Qualifier(\"fixedDiscount\") 최종 금액: " + fixedPrice,
                    "",
                    "  관찰",
                    "  - DiscountPolicy 구현체가 여러 개면 타입만으로는 주입 대상을 결정할 수 없다.",
                    "  - @Qualifier는 Bean 이름으로 주입 대상을 명시한다.",
                    "  - 같은 DiscountPolicy 타입이어도 주입된 구현체에 따라 결과가 달라진다."
                )
            );
        }
    }
}
