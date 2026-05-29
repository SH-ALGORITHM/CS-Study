package com.example.study.stage.s4;

import com.example.study.MeasurementLog;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * STAGE 4-3: 순환 참조 해결 방식 비교.
 */
public class Stage4Resolve {

    @Configuration
    static class LazyResolveConfig {
        @Bean
        public LazyOrderService lazyOrderService(@Lazy LazyDiscountService discountService) {
            return new LazyOrderService(discountService);
        }

        @Bean
        public LazyDiscountService lazyDiscountService(LazyOrderService orderService) {
            return new LazyDiscountService(orderService);
        }
    }

    static class LazyOrderService {
        private final LazyDiscountService discountService;

        LazyOrderService(LazyDiscountService discountService) {
            this.discountService = discountService;
        }
    }

    static class LazyDiscountService {
        private final LazyOrderService orderService;

        LazyDiscountService(LazyOrderService orderService) {
            this.orderService = orderService;
        }
    }

    @Configuration
    static class MediatorConfig {
        @Bean
        public OrderPricingMediator orderPricingMediator() {
            return new OrderPricingMediator();
        }

        @Bean
        public DecoupledOrderService decoupledOrderService(OrderPricingMediator mediator) {
            return new DecoupledOrderService(mediator);
        }

        @Bean
        public DecoupledDiscountService decoupledDiscountService(OrderPricingMediator mediator) {
            return new DecoupledDiscountService(mediator);
        }
    }

    static class OrderPricingMediator {
    }

    static class DecoupledOrderService {
        private final OrderPricingMediator mediator;

        DecoupledOrderService(OrderPricingMediator mediator) {
            this.mediator = mediator;
        }
    }

    static class DecoupledDiscountService {
        private final OrderPricingMediator mediator;

        DecoupledDiscountService(OrderPricingMediator mediator) {
            this.mediator = mediator;
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(LazyResolveConfig.class)) {
            ctx.getBean(LazyOrderService.class);
            ctx.getBean(LazyDiscountService.class);
            System.out.println("@Lazy 해결: 부팅 성공");
        }

        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(MediatorConfig.class)) {
            ctx.getBean(DecoupledOrderService.class);
            ctx.getBean(DecoupledDiscountService.class);
            System.out.println("중재자 분리 해결: 부팅 성공");
        }

        MeasurementLog.save(
            "s4-3",
            "circular reference resolve",
            String.join(System.lineSeparator(),
                "",
                "  @Lazy 해결",
                "  - 한쪽 의존성을 지연 프록시로 주입해 부팅 시점의 즉시 생성 순환을 끊었다.",
                "  - 단, 설계 결합이 사라진 것은 아니므로 임시 해결에 가깝다.",
                "",
                "  중재자 분리 해결",
                "  - OrderService와 DiscountService가 서로 직접 의존하지 않게 공통 협력 객체를 분리했다.",
                "  - 순환 참조 원인을 구조적으로 제거한다.",
                "",
                "  관찰",
                "  - 권장 해결은 @Lazy보다 책임 분리다.",
                "  - @Lazy는 프록시로 생성 시점을 늦추는 방식이고, 설계 결합 자체를 낮추지는 않는다."
            )
        );
    }
}
