package domain;

import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 감사 로그 Aspect — Stage4 양파 껍질에서 @DistributedLock(@Order 1) 안쪽.
 *
 * <p>락 잡은 후 → 감사 기록 → 실제 메서드 → 종료 기록 → 락 해제.
 */
@Aspect
@Component
@Order(2)
public class AuditAspect {

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        long start = System.nanoTime();
        System.out.println("  [AUDIT] before — action=" + audited.action()
            + " method=" + pjp.getSignature().getName()
            + " args=" + Arrays.toString(pjp.getArgs()));
        try {
            Object result = pjp.proceed();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("  [AUDIT] success (" + elapsedMs + "ms)");
            return result;
        } catch (Throwable t) {
            System.out.println("  [AUDIT] fail — " + t.getClass().getSimpleName());
            throw t;
        }
    }
}
