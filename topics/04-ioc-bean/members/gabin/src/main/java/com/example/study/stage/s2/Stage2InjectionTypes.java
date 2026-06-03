package com.example.study.stage.s2;

import com.example.study.MeasurementLog;
import com.example.study.domain.DiscountPolicy;
import com.example.study.domain.PercentDiscount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 2-3: 생성자 / 필드 / 세터 주입 비교.
 */
public class Stage2InjectionTypes {

    private static final int ORDER_AMOUNT = 50_000;

    @Configuration
    static class AppConfig {
        @Bean
        public DiscountPolicy discountPolicy() {
            return new PercentDiscount();
        }

        @Bean
        public ConstructorInjectedOrder constructorInjectedOrder(DiscountPolicy discountPolicy) {
            return new ConstructorInjectedOrder(discountPolicy);
        }

        @Bean
        public FieldInjectedOrder fieldInjectedOrder() {
            return new FieldInjectedOrder();
        }

        @Bean
        public SetterInjectedOrder setterInjectedOrder() {
            return new SetterInjectedOrder();
        }
    }

    static class ConstructorInjectedOrder {
        private final DiscountPolicy discountPolicy;

        public ConstructorInjectedOrder(DiscountPolicy discountPolicy) {
            this.discountPolicy = discountPolicy;
        }

        int finalPrice(int orderAmount) {
            return orderAmount - discountPolicy.discount(orderAmount);
        }
    }

    static class FieldInjectedOrder {
        @Autowired
        private DiscountPolicy discountPolicy;

        int finalPrice(int orderAmount) {
            return orderAmount - discountPolicy.discount(orderAmount);
        }
    }

    static class SetterInjectedOrder {
        private DiscountPolicy discountPolicy;

        @Autowired
        public void setDiscountPolicy(DiscountPolicy discountPolicy) {
            this.discountPolicy = discountPolicy;
        }

        int finalPrice(int orderAmount) {
            return orderAmount - discountPolicy.discount(orderAmount);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            ConstructorInjectedOrder constructorOrder = ctx.getBean(ConstructorInjectedOrder.class);
            FieldInjectedOrder fieldOrder = ctx.getBean(FieldInjectedOrder.class);
            SetterInjectedOrder setterOrder = ctx.getBean(SetterInjectedOrder.class);

            int constructorPrice = constructorOrder.finalPrice(ORDER_AMOUNT);
            int fieldPrice = fieldOrder.finalPrice(ORDER_AMOUNT);
            int setterPrice = setterOrder.finalPrice(ORDER_AMOUNT);

            System.out.println("=== STAGE 2-3: 주입 방식 비교 ===");
            System.out.println("생성자 주입 최종 금액: " + constructorPrice);
            System.out.println("필드 주입 최종 금액: " + fieldPrice);
            System.out.println("세터 주입 최종 금액: " + setterPrice);

            MeasurementLog.save(
                "s2-3",
                "constructor vs field vs setter injection",
                String.join(System.lineSeparator(),
                    "",
                    "  실행 결과",
                    "  - 주문 금액: " + ORDER_AMOUNT,
                    "  - 생성자 주입 최종 금액: " + constructorPrice,
                    "  - 필드 주입 최종 금액: " + fieldPrice,
                    "  - 세터 주입 최종 금액: " + setterPrice,
                    "",
                    "  생성자 주입",
                    "  - final 필드를 사용할 수 있다.",
                    "  - 객체 생성 시 필수 의존성이 반드시 들어온다.",
                    "  - 테스트에서 new ConstructorInjectedOrder(fakePolicy) 형태로 직접 주입하기 쉽다.",
                    "  - 순환 참조가 있으면 생성 시점에 빠르게 드러난다.",
                    "",
                    "  필드 주입",
                    "  - final 필드를 사용할 수 없다.",
                    "  - 객체 외부에서 의존성이 잘 보이지 않는다.",
                    "  - Spring 없이 단위 테스트를 만들기 어렵다.",
                    "",
                    "  세터 주입",
                    "  - 선택 의존성에는 사용할 수 있다.",
                    "  - setter 호출 전까지 의존성이 비어 있을 수 있다.",
                    ""
                )
            );
        }
    }
}
