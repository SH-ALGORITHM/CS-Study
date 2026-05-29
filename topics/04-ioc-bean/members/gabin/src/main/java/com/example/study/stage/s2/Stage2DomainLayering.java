package com.example.study.stage.s2;

import com.example.study.MeasurementLog;
import com.example.study.domain.DiscountPolicy;
import com.example.study.service.OrderService;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 2-2: 할인 정책 도메인을 IoC 계층으로 분리.
 */
public class Stage2DomainLayering {

    private static final int ORDER_AMOUNT = 50_000;

    @Configuration
    @ComponentScan({
        //스캔후 해당 패키지 하위에 있는 클래스들을 빈으로 등록
        "com.example.study.domain",
        "com.example.study.service"
    })
    static class AppConfig {
    }

    public static void main(String[] args) {
        //컨테이너 생성 후 AppConfig.class를 설정 정보로 읽게하는 코드
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            //해당 인터페이스를 구현한 모든 Bean을 이름과 함께 가져얼 수 있는 메서드 getBeansOfType
            Map<String, DiscountPolicy> policies = ctx.getBeansOfType(DiscountPolicy.class);
            OrderService orderService = ctx.getBean(OrderService.class);

            int finalPrice = orderService.calculateFinalPrice(ORDER_AMOUNT);
            int discountAmount = ORDER_AMOUNT - finalPrice;

            System.out.println("=== STAGE 2-2: 할인 정책 도메인 계층 분리 ===");
            System.out.println("DiscountPolicy 구현체 수: " + policies.size());
            policies.forEach((name, policy) ->
                System.out.println("  - " + name + " -> " + policy.getClass().getSimpleName()));
            System.out.println("OrderService 선택 정책: " + orderService.selectedPolicyName());
            System.out.println("주문 금액: " + ORDER_AMOUNT);
            System.out.println("할인 금액: " + discountAmount);
            System.out.println("최종 금액: " + finalPrice);

            String policySummary = policies.entrySet().stream()
                .map(entry -> "  - " + entry.getKey() + " -> " + entry.getValue().getClass().getSimpleName())
                .collect(Collectors.joining(System.lineSeparator()));

            MeasurementLog.save(
                "s2-2",
                "discount domain layering",
                String.join(System.lineSeparator(),
                    "",
                    "  도메인 계층",
                    "  - DiscountPolicy: 할인 계산 추상화",
                    "  - PercentDiscount / FixedDiscount / GradeDiscount: 할인 정책 구현체",
                    "  - OrderService: 주문 금액에 할인 정책을 적용하는 서비스",
                    "",
                    "  등록된 DiscountPolicy Bean",
                    policySummary,
                    "",
                    "  실행 결과",
                    "  - 주문 금액: " + ORDER_AMOUNT,
                    "  - 선택 정책: " + orderService.selectedPolicyName(),
                    "  - 할인 금액: " + discountAmount,
                    "  - 최종 금액: " + finalPrice,
                    ""
                )
            );
        }
    }
}
