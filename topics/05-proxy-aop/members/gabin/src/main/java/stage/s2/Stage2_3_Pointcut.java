package stage.s2;

import domain.Audited;
import infra.MeasurementLog;
import org.aspectj.lang.JoinPoint;
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
@Import({Stage2_3_Pointcut.TargetService.class, Stage2_3_Pointcut.PointcutDemoAspect.class})
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

        @Before("execution(* stage.s2.Stage2_3_Pointcut.TargetService.*(..))")
        public void logExecution(JoinPoint jp) {
            System.out.println("  [exec] " + jp.getSignature().getName());
        }

        @Before("@annotation(domain.Audited)")
        public void logAuditedFqn(JoinPoint jp) {
            System.out.println("  [annot FQN] " + jp.getSignature().getName());
        }

        @Before("@annotation(audited)")
        public void logAuditedBinding(JoinPoint jp, Audited audited) {
            System.out.println("  [annot bind] action=" + audited.action()
                + " method=" + jp.getSignature().getName());
        }

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
        System.out.println("  · execution / @annotation / within 이 같은 메서드에 모두 매칭될 수 있다.");
        System.out.println("  · @annotation 바인딩을 쓰면 어노테이션 속성(action)을 읽을 수 있다.");

        MeasurementLog.save("s2-3", "Pointcut 표현식",
            "execution / @annotation / within 매칭 확인");

        ctx.close();
    }
}
