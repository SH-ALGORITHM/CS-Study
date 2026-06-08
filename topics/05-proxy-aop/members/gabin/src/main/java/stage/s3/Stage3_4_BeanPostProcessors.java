package stage.s3;

import infra.MeasurementLog;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(Stage3_4_BeanPostProcessors.DummyAspect.class)
public class Stage3_4_BeanPostProcessors {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Never {
    }

    @Aspect
    public static class DummyAspect {
        @Before("@annotation(stage.s3.Stage3_4_BeanPostProcessors.Never)")
        public void noop() {
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_4_BeanPostProcessors.class, args);

        MeasurementLog.title("STAGE 3-4 — BeanPostProcessor (internal* / AutoProxy)");

        MeasurementLog.section("internal* + AutoProxy Bean 목록");
        int total = 0;
        boolean foundAutoProxy = false;
        for (String name : ctx.getBeanDefinitionNames()) {
            if (name.contains("internal") || name.toLowerCase().contains("autoproxy")) {
                System.out.println("  - " + name);
                total++;
                foundAutoProxy |= name.toLowerCase().contains("autoproxy");
            }
        }
        MeasurementLog.row("internal* + AutoProxy 개수", total);
        MeasurementLog.row("AutoProxy 포함 여부", foundAutoProxy);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · internalAutoProxyCreator 가 @Aspect를 보고 프록시 생성을 담당한다.");
        System.out.println("  · 총 개수는 Spring Boot 자동 설정에 따라 달라질 수 있다.");

        MeasurementLog.save("s3-4", "BeanPostProcessor 확인",
            "internalOrAutoProxy=" + total + " / autoProxy=" + foundAutoProxy);

        ctx.close();
    }
}
