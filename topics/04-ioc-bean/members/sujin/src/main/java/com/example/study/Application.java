package com.example.study;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 * @SpringBootApplication은 아래 세 가지 역할을 한 번에 켠다.
 *
 * 1. @SpringBootConfiguration
 *    - 이 클래스가 Spring Boot 설정의 시작점임을 표시한다.
 * 2. @ComponentScan
 *    - 현재 패키지 아래의 @Component, @Service, @Repository 등을 Bean으로 자동 등록한다.
 * 3. @EnableAutoConfiguration
 *    - classpath에 있는 라이브러리와 application.yml 같은 설정을 보고
 *      Jackson, TaskExecutor, AOP 등 필요한 인프라 Bean을 자동 등록한다.
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
