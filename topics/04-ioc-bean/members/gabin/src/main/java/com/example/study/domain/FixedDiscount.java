package com.example.study.domain;

import org.springframework.stereotype.Component;

@Component("fixedDiscount")
public class FixedDiscount implements DiscountPolicy {
    @Override
    public int discount(int orderAmount) {
        return Math.min(orderAmount, 10000);
    }
}
