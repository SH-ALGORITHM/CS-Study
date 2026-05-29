package com.example.study.stage.s1;

import com.example.study.MeasurementLog;
import com.example.study.sample.SampleBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * STAGE 1-3: getBean() 직접 조회와 생성자 주입 DI 비교.
 */
public class Stage1GetBeanVsAutowired {

    @Configuration
    @ComponentScan("com.example.study")
    static class AppConfig {
    }

    @Service
    static class LifecycleReportService {
        private final SampleBean sampleBean;

        public LifecycleReportService(SampleBean sampleBean) {
            this.sampleBean = sampleBean;
            System.out.println("  LifecycleReportService 생성자 주입 완료");
        }

        public String report() {
            return "DI로 받은 Bean = " + sampleBean.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            System.out.println("=== 방법 A: getBean() 직접 조회 ===");
            SampleBean directBean = ctx.getBean(SampleBean.class);
            System.out.println("직접 조회 결과: " + directBean.getClass().getSimpleName());

            System.out.println();
            System.out.println("=== 방법 B: 생성자 주입 DI ===");
            LifecycleReportService service = ctx.getBean(LifecycleReportService.class);
            System.out.println(service.report());

            MeasurementLog.save(
                "s1-3",
                "getBean() vs constructor DI",
                String.join(System.lineSeparator(),
                    "",
                    "  getBean() 직접 조회",
                    "  - 호출 코드가 ApplicationContext를 직접 알고 있다.",
                    "  - 필요한 Bean을 사용할 때마다 ctx.getBean(SampleBean.class)로 꺼낸다.",
                    "  - 조회 결과: " + directBean.getClass().getSimpleName(),
                    "",
                    "  생성자 주입 DI",
                    "  - LifecycleReportService는 ApplicationContext를 모른다.",
                    "  - 생성자 파라미터로 SampleBean 필요성을 선언한다.",
                    "  - Spring이 SampleBean을 찾아 생성자 인자로 넣어준다.",
                    "  - 실행 결과: " + service.report(),
                    "",
                    "  관찰",
                    "  - getBean()은 Service Locator 방식에 가깝고 코드가 Spring 컨테이너에 직접 의존한다.",
                    "  - 생성자 주입은 의존성이 생성자에 명시되고, 서비스는 컨테이너가 아니라 필요한 객체에만 의존한다."
                )
            );
        }
    }
}
