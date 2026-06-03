package com.example.study.domain;

// 할인정책 인터페이스
public interface DiscountPolicy {
    int discount(int orderAmount);
}
