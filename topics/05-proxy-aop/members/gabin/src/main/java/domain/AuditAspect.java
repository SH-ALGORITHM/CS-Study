package domain;

import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(2)
public class AuditAspect {

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("[AUDIT] user=gabin"
                + " action=" + audited.action()
                + " method=" + pjp.getSignature().getName()
                + " args=" + Arrays.toString(pjp.getArgs())
                + " result=SUCCESS elapsed=" + elapsedMs + "ms");
            return result;
        } catch (Throwable t) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.println("[AUDIT] user=gabin"
                + " action=" + audited.action()
                + " method=" + pjp.getSignature().getName()
                + " result=FAIL exception=" + t.getClass().getSimpleName()
                + " elapsed=" + elapsedMs + "ms");
            throw t;
        }
    }
}
