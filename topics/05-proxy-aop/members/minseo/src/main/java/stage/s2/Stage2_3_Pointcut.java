package stage.s2;

import domain.Audited;
import infra.MeasurementLog;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * STAGE 2-3 — Pointcut 표현식 실습.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {"stage.s2", "domain", "infra"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            Stage2_1_NaiveTrap.class,
            Stage2_1_ThreadLocal.class,
            Stage2_2_OrderChaining.class
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

        // (1) execution — 패키지 + 메서드 패턴 (가장 정밀함)
        @Before("execution(* stage.s2.Stage2_3_Pointcut.TargetService.*(..))")
        public void logExecution(JoinPoint jp) {
            System.out.println("  [exec] 메서드명: " + jp.getSignature().getName());
        }

        // (2) @annotation — 특정 어노테이션이 붙은 경우
        @Before("@annotation(domain.Audited)")
        public void logAuditedFqn(JoinPoint jp) {
            System.out.println("  [annot FQN] @Audited 감지됨");
        }

        // (3) @annotation 파라미터 바인딩 — 어노테이션의 속성(action)을 꺼내 쓰고 싶을 때
        @Before("@annotation(audited)")
        public void logAuditedBinding(JoinPoint jp, Audited audited) {
            System.out.println("  [annot bind] 어노테이션 속성 action=" + audited.action());
        }

        // (4) within — 특정 클래스 내부의 모든 메서드
        @Before("within(stage.s2.Stage2_3_Pointcut.TargetService)")
        public void logWithin(JoinPoint jp) {
            System.out.println("  [within] TargetService 클래스 내 메서드 호출됨");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_3_Pointcut.class, args);
        TargetService svc = ctx.getBean(TargetService.class);

        MeasurementLog.title("STAGE 2-3 — Pointcut 표현식 실습");
        
        MeasurementLog.section("svc.doWork(\"hi\") 호출 — 4개 Pointcut 모두 매칭 예정");
        String result = svc.doWork("hi");
        
        MeasurementLog.row("최종 결과", result);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · 하나의 메서드에 여러 Pointcut이 겹쳐도 모두 실행됨");
        System.out.println("  · execution은 이름 기반, @annotation은 마킹 기반, within은 소속 기반");
        System.out.println("  · 어노테이션의 속성(action)을 쓰려면 파라미터 바인딩 방식(3번)을 써야 함");

        ctx.close();
    }
}
