package com.example.study.stage.s3;

import com.example.study.MeasurementLog;
import com.example.study.domain.PercentDiscount;
import com.example.study.service.OrderService;

/**
 * STAGE 3-1 A: Spring 없이 순수 Java 객체 생성 시간 측정.
 */
public class Stage3_A_Pure {

    public static void main(String[] args) {
        long start = System.nanoTime();

        PercentDiscount discountPolicy = new PercentDiscount();
        OrderService orderService = new OrderService(discountPolicy);
        int finalPrice = orderService.calculateFinalPrice(50_000);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("=== STAGE 3-A: pure main ===");
        System.out.println("final price: " + finalPrice);
        System.out.println("elapsed: " + elapsedMs + "ms");

        MeasurementLog.save(
            "s3-1",
            "pure main",
            "객체 2개 직접 생성 / 최종 금액 " + finalPrice + " / " + elapsedMs + "ms"
        );
    }
}
