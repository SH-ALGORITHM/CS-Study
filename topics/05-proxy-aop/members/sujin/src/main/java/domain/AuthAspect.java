package domain;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1)          // 가장 바깥 — 권한부터 검사
public class AuthAspect {

    // 파라미터 바인딩: @annotation(requireRole) → 메서드 인자 RequireRole 로 어노테이션 객체 받음
    @Before("@annotation(requireRole)")
    public void check(JoinPoint jp, RequireRole requireRole) {
        String current  = SecurityContext.getRole();
        String required = requireRole.value();
        System.out.println("[AUTH] " + jp.getSignature().getName()
            + " 요구=" + required + " 현재=" + current);

        if (current == null || !current.equals(required)) {
            // 여기서 예외 → proceed 자체가 안 일어남 → 진짜 메서드 호출 차단
            throw new AccessDeniedException(
                "권한 부족: " + required + " 필요 (현재: " + current + ")");
        }
    }
}
