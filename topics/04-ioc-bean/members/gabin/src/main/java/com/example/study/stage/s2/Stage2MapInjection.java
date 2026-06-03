package com.example.study.stage.s2;

import com.example.study.MeasurementLog;
import com.example.study.domain.DiscountPolicy;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 2-4 확장: Map<String, DiscountPolicy>로 모든 정책을 주입받아 선택.
 */
public class Stage2MapInjection {

    private static final int ORDER_AMOUNT = 50_000;

    @Configuration
    @ComponentScan("com.example.study.domain")
    static class AppConfig {
        @Bean
        public DiscountCalculator discountCalculator(Map<String, DiscountPolicy> policies) {
            return new DiscountCalculator(policies);
        }

        static class DiscountCalculator {
            private final Map<String, DiscountPolicy> policies;

            //spring이 해당 인터페이스 타입의 Bean들을 생성 시 모아서 Map에 넣어줌.
            DiscountCalculator(Map<String, DiscountPolicy> policies) {
                this.policies = policies;
            }

            int finalPrice(String policyName, int orderAmount) {
                DiscountPolicy policy = policies.get(policyName);
                if (policy == null) {
                    throw new IllegalArgumentException("Unknown discount policy: " + policyName);
                }
                return orderAmount - policy.discount(orderAmount);
            }

            Map<String, DiscountPolicy> policies() {
                return policies;
            }
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            AppConfig.DiscountCalculator calculator = ctx.getBean(AppConfig.DiscountCalculator.class);

            String results = calculator.policies().keySet().stream()
                .sorted()
                .map(name -> "  - " + name + ": " + calculator.finalPrice(name, ORDER_AMOUNT))
                .collect(Collectors.joining(System.lineSeparator()));

            System.out.println("=== STAGE 2-4: Map injection ===");
            System.out.println(results);

            MeasurementLog.save(
                "s2-4",
                "Map<String, DiscountPolicy>",
                String.join(System.lineSeparator(),
                    "",
                    "  주문 금액: " + ORDER_AMOUNT,
                    "  정책별 최종 금액",
                    results,
                    "",
                    "  관찰",
                    "  - Map의 key는 Bean 이름이다.",
                    "  - @Component(\"percentDiscount\")로 지정한 이름이 Map key가 된다.",
                    "  - 새 정책을 추가하면 Map에 자동으로 포함되므로 OCP를 설명하기 좋다."
                )
            );
        }
    }
}
