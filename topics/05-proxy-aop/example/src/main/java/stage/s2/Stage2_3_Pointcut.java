package stage.s2;

import domain.Audited;
import infra.MeasurementLog;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * STAGE 2-3 — Pointcut 표현식 3 가지.
 *
 * <p>같은 메서드가 3 개 Pointcut 에 모두 매칭되면 advice 3 번 호출.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = "stage.s2",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            Stage2_1_NaiveTrap.class,
            Stage2_1_ThreadLocal.class,
            Stage2_2_OrderChaining.class,
            Stage2_4_FiveAdvice.class,
            Stage2_5_Audited.class
        }
    )
)
public class Stage2_3_Pointcut {

    @Service
    public static class TargetService {
        @Audited(action = "DEMO")
        public String doWork(String input) {
            return "processed: " + input;
        }
    }

    @Aspect
    @Component
    public static class PointcutDemoAspect {

        // (1) execution — 패키지 + 메서드 패턴
        @Before("execution(* stage.s2.Stage2_3_Pointcut.TargetService.*(..))")
        public void logExecution(JoinPoint jp) {
            System.out.println("  [exec] " + jp.getSignature().getName());
        }

        // (2) @annotation FQN — 어노테이션 타입 매칭만 (객체 안 받음)
        @Before("@annotation(domain.Audited)")
        public void logAuditedFqn(JoinPoint jp) {
            System.out.println("  [annot FQN] " + jp.getSignature().getName());
        }

        // (3) @annotation 파라미터 바인딩 — 어노테이션 객체 받음 (속성 접근 가능)
        @Before("@annotation(audited)")
        public void logAuditedBinding(JoinPoint jp, Audited audited) {
            System.out.println("  [annot bind] action=" + audited.action()
                + " method=" + jp.getSignature().getName());
        }

        // (4) within — 특정 클래스 내 모든 메서드
        @Before("within(stage.s2.Stage2_3_Pointcut.TargetService)")
        public void logWithin(JoinPoint jp) {
            System.out.println("  [within] " + jp.getSignature().getName());
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_3_Pointcut.class, args);
        TargetService svc = ctx.getBean(TargetService.class);

        MeasurementLog.title("STAGE 2-3 — Pointcut 표현식 3 가지");
        MeasurementLog.section("svc.doWork(\"hi\") 호출 — 4 개 advice 모두 매칭");
        String result = svc.doWork("hi");
        MeasurementLog.row("결과", result);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · execution / @annotation / within — 같은 메서드에 다 매칭되면 4 번 호출");
        System.out.println("  · @annotation FQN 은 타입만 / 파라미터 바인딩은 어노테이션 객체 자체 받음");
        System.out.println("  · 파라미터 바인딩 = action 속성 (\"DEMO\") 접근 가능");

        ctx.close();
    }
}
