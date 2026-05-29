package com.example.study.stage.s4;

import com.example.study.MeasurementLog;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 4-1: 생성자 기반 순환 참조 실패 관찰.
 */
public class Stage4Circular {

    @Configuration
    static class CircularConfig {
        @Bean
        public OrderCircularService orderCircularService(DiscountCircularService discountService) {
            return new OrderCircularService(discountService);
        }

        @Bean
        public DiscountCircularService discountCircularService(OrderCircularService orderService) {
            return new DiscountCircularService(orderService);
        }
    }

    static class OrderCircularService {
        private final DiscountCircularService discountService;

        OrderCircularService(DiscountCircularService discountService) {
            this.discountService = discountService;
        }
    }

    static class DiscountCircularService {
        private final OrderCircularService orderService;

        DiscountCircularService(OrderCircularService orderService) {
            this.orderService = orderService;
        }
    }

    public static void main(String[] args) {
        String result;
        try (AnnotationConfigApplicationContext ignored =
                 new AnnotationConfigApplicationContext(CircularConfig.class)) {
            result = "unexpected success";
        } catch (BeanCreationException e) {
            result = e.getClass().getSimpleName();
            System.out.println("=== STAGE 4-1: constructor circular reference ===");
            System.out.println("부팅 실패: " + result);
            System.out.println(e.getMostSpecificCause().getMessage());
        }

        MeasurementLog.save(
            "s4-1",
            "constructor circular reference",
            String.join(System.lineSeparator(),
                "",
                "  결과: " + result,
                "",
                "  관찰",
                "  - OrderCircularService 생성에 DiscountCircularService가 필요하다.",
                "  - DiscountCircularService 생성에 다시 OrderCircularService가 필요하다.",
                "  - 생성자 주입은 객체 생성 전에 모든 필수 의존성이 필요하므로 순환 참조가 부팅 시점에 드러난다.",
                "  - 해결 방향은 설계 분리, 중재자 도입, 또는 불가피한 경우 @Lazy 사용이다."
            )
        );
    }
}
