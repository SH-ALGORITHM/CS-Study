package domain;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PointcutDemoAspect {

    // (1) execution — 패키지 + 메서드 패턴
    @Before("execution(* domain..*Service.*(..))")
    public void byExecution(JoinPoint jp) {
        System.out.println("[exec]   " + jp.getSignature().toShortString());
    }

    // (2) @annotation — 특정 어노테이션 붙은 메서드만 (선택적)
    @Before("@annotation(domain.RequireRole)")
    public void byAnnotation(JoinPoint jp) {
        System.out.println("[anno]   " + jp.getSignature().toShortString());
    }

    // (3) within — 특정 클래스 내 모든 메서드
    @Before("within(domain.AdminTaskService)")
    public void byWithin(JoinPoint jp) {
        System.out.println("[within] " + jp.getSignature().toShortString());
    }
}
