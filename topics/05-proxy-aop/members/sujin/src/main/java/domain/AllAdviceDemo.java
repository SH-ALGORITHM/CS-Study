package domain;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AllAdviceDemo {

    @Before("@annotation(Traced)")
    public void before(JoinPoint jp) { System.out.println("[1 Before]"); }

    @Around("@annotation(Traced)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("[2 Around 시작]");
        try {
            Object r = pjp.proceed();
            System.out.println("[2 Around 정상 종료]");
            return r;
        } catch (Throwable t) {
            System.out.println("[2 Around 예외]");
            throw t;
        }
    }

    @AfterReturning(pointcut = "@annotation(Traced)", returning = "result")
    public void afterReturning(JoinPoint jp, Object result) {
        System.out.println("[3 AfterReturning] = " + result);
    }

    @AfterThrowing(pointcut = "@annotation(Traced)", throwing = "ex")
    public void afterThrowing(JoinPoint jp, Throwable ex) {
        System.out.println("[4 AfterThrowing] " + ex.getMessage());
    }

    @After("@annotation(Traced)")
    public void after(JoinPoint jp) { System.out.println("[5 After]"); }
}
