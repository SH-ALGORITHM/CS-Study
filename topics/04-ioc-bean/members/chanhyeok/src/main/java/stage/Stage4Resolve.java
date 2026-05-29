package stage;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * STAGE 4-3 — 결제 ↔ 환불 순환 참조 해결 (@Lazy / Mediator 분리).
 *
 * <h3>해결 2 가지</h3>
 * <ol>
 *   <li>@Lazy: 한쪽을 프록시 주입으로 → 닭·달걀 회피. 동작은 하지만 설계 냄새</li>
 *   <li>Mediator: 공통 책임 (PaymentLogger) 을 제 3 자로 분리 → 근본 해결</li>
 * </ol>
 */
public class Stage4Resolve {

    // ============ 해결 1: @Lazy (한쪽만) ============
    @Component
    static class LazyPaymentService {
        private final LazyRefundService refund;
        public LazyPaymentService(@Lazy LazyRefundService refund) {
            System.out.println("  [LazyPaymentService] 생성자 — refund 는 프록시: "
                + refund.getClass().getSimpleName());
            this.refund = refund;
        }
        public String label() { return "Payment"; }
        public String useRefund() { return refund.label(); }
    }
    @Component
    static class LazyRefundService {
        private final LazyPaymentService payment;
        public LazyRefundService(LazyPaymentService payment) {
            System.out.println("  [LazyRefundService] 생성자 — payment 이미 주입됨 (실제 LazyPaymentService)");
            this.payment = payment;
        }
        public String label() { return "Refund"; }
    }

    // ============ 해결 2: Mediator 분리 (공통 책임 → PaymentLogger) ============
    @Component
    static class PaymentLogger {
        public void log(String event) { System.out.println("  [Log] " + event); }
    }
    @Component
    static class CleanPaymentService {
        private final PaymentLogger logger;
        public CleanPaymentService(PaymentLogger logger) { this.logger = logger; }
        public void pay() { logger.log("결제 완료"); }
    }
    @Component
    static class CleanRefundService {
        private final PaymentLogger logger;
        public CleanRefundService(PaymentLogger logger) { this.logger = logger; }
        public void refund() { logger.log("환불 완료"); }
    }

    public static void main(String[] args) {
        System.out.println("=== 해결 1: @Lazy 로 한쪽 프록시 주입 ===");
        var ctx1 = new AnnotationConfigApplicationContext();
        ctx1.register(LazyPaymentService.class, LazyRefundService.class);
        ctx1.refresh();
        System.out.println("  부팅 성공 — @Lazy 가 닭·달걀 회피");
        LazyPaymentService p = ctx1.getBean(LazyPaymentService.class);
        System.out.println("  p.useRefund() = " + p.useRefund());
        ctx1.close();

        System.out.println("\n=== 해결 2: Mediator (PaymentLogger) 분리 — 설계 재검토 ===");
        var ctx2 = new AnnotationConfigApplicationContext();
        ctx2.register(PaymentLogger.class, CleanPaymentService.class, CleanRefundService.class);
        ctx2.refresh();
        System.out.println("  부팅 성공 — PaymentService 와 RefundService 가 서로 의존 X");
        ctx2.getBean(CleanPaymentService.class).pay();
        ctx2.getBean(CleanRefundService.class).refund();
        ctx2.close();

        System.out.println("\n[학습 포인트]");
        System.out.println("  해결 1 (@Lazy): 동작하지만 설계 냄새 — 진짜 양방향이 필요한지 다시 생각");
        System.out.println("  해결 2 (Mediator): 근본 해결. PaymentService ↔ RefundService 직결 → 둘 다 Logger 만 의존");
        System.out.println("  실무: 일단 @Lazy 로 막고, PR 리뷰에서 재설계 검토 — 흔한 패턴");
    }
}
