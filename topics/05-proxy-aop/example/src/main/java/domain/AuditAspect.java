package domain;

import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 감사 로그 Aspect — @Audited 붙은 메서드의 호출 전후를 자동 기록.
 *
 * <p>{@code @Order(2)} = MyTransactionalAspect(@Order(1)) 안쪽.
 * 트랜잭션이 가장 바깥 → 감사는 commit 전에 기록 (실패 시 rollback 됨을 가정).
 */
@Aspect
@Component
@Order(2)
public class AuditAspect {

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        long start = System.nanoTime();
        String userId = currentUserIdMock();
        try {
            Object result = pjp.proceed();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("[AUDIT] user=" + userId
                + " action=" + audited.action()
                + " method=" + pjp.getSignature().getName()
                + " args=" + Arrays.toString(pjp.getArgs())
                + " result=SUCCESS elapsed=" + elapsedMs + "ms");
            return result;
        } catch (Throwable t) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("[AUDIT] user=" + userId
                + " action=" + audited.action()
                + " method=" + pjp.getSignature().getName()
                + " result=FAIL exception=" + t.getClass().getSimpleName()
                + " elapsed=" + elapsedMs + "ms");
            throw t;
        }
    }

    /** SecurityContext 대신 가짜 사용자 ID. 11 주차 학습 전까지 모킹. */
    private String currentUserIdMock() {
        return "user-42";
    }
}
