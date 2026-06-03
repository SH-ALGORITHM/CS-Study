package stage.s2;

import infra.MeasurementLog;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * STAGE 2-4 — Advice 5 종 호출 순서 확인.
 */
@SpringBootApplication(scanBasePackages = "stage.s2")
@ComponentScan(
    basePackages = {"stage.s2", "domain", "infra"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            Stage2_1_NaiveTrap.class,
            Stage2_1_ThreadLocal.class,
            Stage2_2_OrderChaining.class,
            Stage2_3_Pointcut.class
        }
    )
)
public class Stage2_4_FiveAdvice {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Traced {}

    @Service
    public static class TracedService {
        @Traced
        public String doSuccess() { return "ok"; }

        @Traced
        public String doFail() { throw new RuntimeException("boom"); }
    }

    @Aspect
    @Component
    public static class FiveAdviceAspect {

        @Before("@annotation(stage.s2.Stage2_4_FiveAdvice$Traced)")
        public void beforeAdvice(JoinPoint jp) {
            System.out.println("  [1 Before]");
        }

        @After("@annotation(stage.s2.Stage2_4_FiveAdvice$Traced)")
        public void afterAdvice(JoinPoint jp) {
            System.out.println("  [5 After] (finally)");
        }

        @AfterReturning(
            pointcut = "@annotation(stage.s2.Stage2_4_FiveAdvice$Traced)",
            returning = "result"
        )
        public void afterReturning(JoinPoint jp, Object result) {
            System.out.println("  [3 AfterReturning] 결과=" + result);
        }

        @AfterThrowing(
            pointcut = "@annotation(stage.s2.Stage2_4_FiveAdvice$Traced)",
            throwing = "ex"
        )
        public void afterThrowing(JoinPoint jp, Throwable ex) {
            System.out.println("  [4 AfterThrowing] 에러=" + ex.getMessage());
        }

        @Around("@annotation(stage.s2.Stage2_4_FiveAdvice$Traced)")
        public Object aroundAdvice(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("[2 Around 시작]");
            try {
                Object result = pjp.proceed();
                System.out.println("[2 Around 정상 종료]");
                return result;
            } catch (Throwable t) {
                System.out.println("[2 Around 예외 처리]");
                throw t;
            }
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_4_FiveAdvice.class, args);
        TracedService svc = ctx.getBean(TracedService.class);

        MeasurementLog.title("STAGE 2-4 — Advice 5 종 호출 순서");

        MeasurementLog.section("1. doSuccess() 호출 (정상)");
        svc.doSuccess();

        MeasurementLog.section("2. doFail() 호출 (예외)");
        try {
            svc.doFail();
        } catch (RuntimeException e) {
            System.out.println("  Main에서 잡음: " + e.getMessage());
        }

        ctx.close();
    }
}
