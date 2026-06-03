package com.example.study.service;

import com.example.study.domain.DiscountPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

//구현체가 3개이기에 그냥 주입하면 Spring이 다중 구현체 중 어떤 Bean을 넣을지 몰라 컴파일 에러 발생. 따라서 @Qualifier 사용
@Service
public class OrderService {
    private final DiscountPolicy discountPolicy;

    public OrderService(@Qualifier("percentDiscount") DiscountPolicy discountPolicy) {
        this.discountPolicy = discountPolicy;
    }

    public int calculateFinalPrice(int orderAmount) {
        return orderAmount - discountPolicy.discount(orderAmount);
    }

    public String selectedPolicyName() {
        return discountPolicy.getClass().getSimpleName();
    }
}
