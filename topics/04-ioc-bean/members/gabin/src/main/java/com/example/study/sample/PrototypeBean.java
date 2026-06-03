package com.example.study.sample;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class PrototypeBean {
    public PrototypeBean() {
        System.out.println("  [생성] PrototypeBean 생성자");
    }
    @PostConstruct
    public void init() {
        System.out.println("  [생성] PrototypeBean @PostConstruct");
    }
    @PreDestroy
    public void destroy() {
        System.out.println("  ⚠️ PrototypeBean @PreDestroy — 호출되면 안 됨");
    }
}
