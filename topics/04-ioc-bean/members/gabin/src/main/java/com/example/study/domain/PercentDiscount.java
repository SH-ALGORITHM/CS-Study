package com.example.study.domain;

import org.springframework.stereotype.Component;

@Component("percentDiscount")
public class PercentDiscount implements DiscountPolicy {
    @Override
    public int discount(int orderAmount) {
        return (int) (orderAmount * 0.2);
    }
}
