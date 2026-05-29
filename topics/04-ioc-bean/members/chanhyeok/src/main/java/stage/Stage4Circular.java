package stage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * STAGE 4-1, 4-2 — 결제 ↔ 환불 순환 참조 시연.
 *
 * <h3>시나리오</h3>
 * 결제 완료 후 환불 가능 / 환불 시 결제 정보 필요 → 자연스러운 순환 가능성.
 * 실무에서는 잘못된 설계 — 학습용으로 의도적으로 만든다.
 *
 * <h3>3 가지 시도</h3>
 * <ol>
 *   <li>생성자 순환 (어떤 설정이든 실패)</li>
 *   <li>필드 순환 (allow=true 기본 — 통과)</li>
 *   <li>필드 순환 + setAllowCircularReferences(false) (Spring Boot 2.6+ 동작)</li>
 * </ol>
 */
public class Stage4Circular {

    // ============ 생성자 순환 ============
    @Component
    static class CtorPaymentService {
        private final CtorRefundService refund;
        public CtorPaymentService(CtorRefundService refund) { this.refund = refund; }
    }
    @Component
    static class CtorRefundService {
        private final CtorPaymentService payment;
        public CtorRefundService(CtorPaymentService payment) { this.payment = payment; }
    }

    // ============ 필드 순환 ============
    @Component
    static class FieldPaymentService {
        @Autowired private FieldRefundService refund;
        public String describe() { return "PaymentService → " + refund.label(); }
        public String label() { return "Payment"; }
    }
    @Component
    static class FieldRefundService {
        @Autowired private FieldPaymentService payment;
        public String describe() { return "RefundService → " + payment.label(); }
        public String label() { return "Refund"; }
    }

    public static void main(String[] args) {
        int failures = 0;

        System.out.println("=== 시나리오 1: 생성자 순환 (어떤 설정에서도 실패) ===");
        try {
            var ctx = new AnnotationConfigApplicationContext();
            ctx.register(CtorPaymentService.class, CtorRefundService.class);
            ctx.refresh();
            System.out.println("  통과? (여기 도달하면 안 됨)");
            ctx.close();
        } catch (Exception e) {
            failures++;
            System.out.println("  부팅 실패: " + e.getClass().getSimpleName());
            System.out.println("  원인: " + extractRootCause(e));
        }

        System.out.println("\n=== 시나리오 2: 필드 순환 (allow=true 기본) — 통과 ===");
        try {
            var ctx = new AnnotationConfigApplicationContext();
            ctx.register(FieldPaymentService.class, FieldRefundService.class);
            ctx.refresh();
            System.out.println("  부팅 성공 — 빈 껍데기 메커니즘으로 통과");
            FieldPaymentService payment = ctx.getBean(FieldPaymentService.class);
            System.out.println("  호출: " + payment.describe());
            ctx.close();
        } catch (Exception e) {
            System.out.println("  실패: " + e.getClass().getSimpleName());
        }

        System.out.println("\n=== 시나리오 3: 필드 순환 + setAllowCircularReferences(false) ===");
        System.out.println("  (Spring Boot 2.6+ 의 기본 동작과 동일)");
        try {
            var ctx = new AnnotationConfigApplicationContext();
            ctx.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            ctx.register(FieldPaymentService.class, FieldRefundService.class);
            ctx.refresh();
            System.out.println("  통과? (여기 도달하면 안 됨)");
            ctx.close();
        } catch (Exception e) {
            failures++;
            System.out.println("  부팅 실패: " + e.getClass().getSimpleName());
            System.out.println("  → Spring Boot 2.6+ 가 막은 동작");
        }

        System.out.println("\n[학습 포인트]");
        System.out.println("  생성자: 닭·달걀 (둘 다 못 만듦) → 항상 실패");
        System.out.println("  필드/세터 (allow=true): 빈 껍데기 만든 후 주입 → 통과 (불완전 상태 호출 시 NPE)");
        System.out.println("  Spring Boot 2.6+ (allow=false 기본): 빈 껍데기 메커니즘 차단 → 모두 실패");
        System.out.println("  → 막은 이유: 다른 스레드가 초기화 미완성 인스턴스 사용 → NPE / 부분 동작");
    }

    private static String extractRootCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
