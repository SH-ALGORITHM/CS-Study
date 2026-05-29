package com.example.study.sample;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class SampleBean {
    public SampleBean() {
        System.out.println("  [1] SampleBean 생성자 호출");
    }
    @PostConstruct
    public void init() {
        System.out.println("  [2] SampleBean @PostConstruct");
    }
    @PreDestroy
    public void destroy() {
        System.out.println("  [4] SampleBean @PreDestroy");
    }
}
