package com.example.study.domain;

import org.springframework.stereotype.Component;

@Component("gradeDiscount")
public class GradeDiscount implements DiscountPolicy {
    @Override
    public int discount(int orderAmount) {
        return (int) (orderAmount * 0.15);
    }
}
