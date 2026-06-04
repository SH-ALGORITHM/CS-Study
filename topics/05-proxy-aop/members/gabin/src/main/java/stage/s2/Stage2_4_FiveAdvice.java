package stage.s2;

import infra.MeasurementLog;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({Stage2_4_FiveAdvice.TracedService.class, Stage2_4_FiveAdvice.FiveAdviceAspect.class})
public class Stage2_4_FiveAdvice {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Traced {
    }

    @Service
    public static class TracedService {
        @Traced
        public String doSuccess() {
            return "ok";
        }

        @Traced
        public String doFail() {
            throw new RuntimeException("boom");
        }
    }

    @Aspect
    @Component
    public static class FiveAdviceAspect {

        @Before("@annotation(stage.s2.Stage2_4_FiveAdvice$Traced)")
        public void beforeAdvice(JoinPoint jp) {
            System.out.println("[1 Before]");
        }

        @After("@annotation(stage.s2.Stage2_4_FiveAdvice$Traced)")
        public void afterAdvice(JoinPoint jp) {
            System.out.println("[5 After] finally");
        }

        @AfterReturning(
            pointcut = "@annotation(stage.s2.Stage2_4_FiveAdvice$Traced)",
            returning = "result"
        )
        public void afterReturning(JoinPoint jp, Object result) {
            System.out.println("[3 AfterReturning] = " + result);
        }

        @AfterThrowing(
            pointcut = "@annotation(stage.s2.Stage2_4_FiveAdvice$Traced)",
            throwing = "ex"
        )
        public void afterThrowing(JoinPoint jp, Throwable ex) {
            System.out.println("[4 AfterThrowing] " + ex.getMessage());
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

        MeasurementLog.section("doSuccess() — 정상 종료");
        svc.doSuccess();

        MeasurementLog.section("doFail() — 예외 발생");
        try {
            svc.doFail();
        } catch (RuntimeException e) {
            System.out.println("  catch: " + e.getMessage());
        }

        MeasurementLog.section("학습 포인트");
        System.out.println("  · Around 가 가장 바깥이다.");
        System.out.println("  · 정상: Around 시작 → Before → 실제 메서드 → AfterReturning → After → Around 종료");
        System.out.println("  · 예외: Around 시작 → Before → 실제 메서드 → AfterThrowing → After → Around 예외 처리");

        MeasurementLog.save("s2-4", "Advice 5 종 호출 순서",
            "Around 가 가장 바깥 / 정상과 예외 순서 확인");

        ctx.close();
    }
}
