package com.example.study.stage.s1;

import com.example.study.MeasurementLog;
import com.example.study.sample.SampleBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 1-2: @ComponentScan vs @Bean 직접 등록 차이.
 **/
public class Stage1ScanVsBean {

    @Configuration
    @ComponentScan("com.example.study.sample")
    static class ComponentScanConfig {
    }

    @Configuration
    static class BeanConfig {
        @Bean
        public SampleBean sampleBean() {
            return new SampleBean();
        }
    }

    public static void main(String[] args) {
        String scanResult;
        String beanResult;

        System.out.println("=== @ComponentScan 방식 ===");
        try (AnnotationConfigApplicationContext scanCtx =
                 new AnnotationConfigApplicationContext(ComponentScanConfig.class)) {

            printDefinitions(scanCtx);

            SampleBean sampleBean = scanCtx.getBean(SampleBean.class);
            System.out.println("조회 결과: " + sampleBean.getClass().getSimpleName());

            scanResult = summarize(scanCtx, "sampleBean");
        }

        System.out.println();
        System.out.println("=== @Bean 직접 등록 방식 ===");
        try (AnnotationConfigApplicationContext beanCtx =
                 new AnnotationConfigApplicationContext(BeanConfig.class)) {

            printDefinitions(beanCtx);

            SampleBean sampleBean = beanCtx.getBean("sampleBean", SampleBean.class);
            System.out.println("조회 결과: " + sampleBean.getClass().getSimpleName());

            beanResult = summarize(beanCtx, "sampleBean");
        }

        MeasurementLog.save(
            "s1-2",
            "@ComponentScan vs @Bean",
            String.join(System.lineSeparator(),
                "",
                "  @ComponentScan 방식",
                scanResult,
                "  - ctx.getBean(SampleBean.class)로 SampleBean 조회 성공",
                "",
                "  @Bean 직접 등록 방식",
                beanResult,
                "  - ctx.getBean(\"sampleBean\", SampleBean.class)로 SampleBean 조회 성공",
                "",
                "  관찰",
                "  - @ComponentScan은 지정한 패키지 아래의 @Component 클래스를 자동 등록한다.",
                "  - @Bean은 @Configuration 클래스의 메서드 반환 객체를 직접 Bean으로 등록한다.",
                "  - 외부 라이브러리 객체처럼 @Component를 붙일 수 없는 객체는 @Bean 방식으로 등록한다."
            )
        );
    }

    private static void printDefinitions(AnnotationConfigApplicationContext ctx) {
        for (String name : ctx.getBeanDefinitionNames()) {
            BeanDefinition definition = ctx.getBeanDefinition(name);
            System.out.println("bean name = " + name);
            System.out.println("  source = " + describeSource(definition));
            System.out.println("  scope = " + normalizedScope(definition));
        }
    }

    private static String summarize(AnnotationConfigApplicationContext ctx, String beanName) {
        BeanDefinition definition = ctx.getBeanDefinition(beanName);
        return String.join(System.lineSeparator(),
            "  - Bean 이름: " + beanName,
            "  - 등록 정보: " + describeSource(definition),
            "  - Scope: " + normalizedScope(definition)
        );
    }

    private static String describeSource(BeanDefinition definition) {
        if (definition.getBeanClassName() != null) {
            return definition.getBeanClassName();
        }
        if (definition.getFactoryBeanName() != null && definition.getFactoryMethodName() != null) {
            return definition.getFactoryBeanName() + "#" + definition.getFactoryMethodName() + "()";
        }
        return "unknown";
    }

    private static String normalizedScope(BeanDefinition definition) {
        String scope = definition.getScope();
        return scope == null || scope.isBlank() ? "singleton(default)" : scope;
    }
}
