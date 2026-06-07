package domain;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

import java.util.Arrays;

@Aspect
@Order(2)
public class AuditAspect {

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        long start = System.nanoTime();
        String userId = currentUserIdMock();
        
        // [수정] 시작 로그 추가! 이제 [TX] begin 처럼 시작 시점에도 보입니다.
        System.out.println("  [AUDIT] 시작 - Action: " + audited.action());

        try {
            Object result = pjp.proceed();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("  [AUDIT] 성공 - user=" + userId
                + " action=" + audited.action()
                + " method=" + pjp.getSignature().getName()
                + " args=" + Arrays.toString(pjp.getArgs())
                + " result=SUCCESS elapsed=" + elapsedMs + "ms");
            return result;
        } catch (Throwable t) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("  [AUDIT] 실패 - user=" + userId
                + " action=" + audited.action()
                + " method=" + pjp.getSignature().getName()
                + " result=FAIL exception=" + t.getClass().getSimpleName()
                + " elapsed=" + elapsedMs + "ms");
            throw t;
        }
    }

    private String currentUserIdMock() {
        return "user-42";
    }
}
