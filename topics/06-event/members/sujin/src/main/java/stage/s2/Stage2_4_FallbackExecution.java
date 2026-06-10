package stage.s2;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// ─────────────────────────────────────────────────────────────
// STAGE 2-4 — fallbackExecution: 트랜잭션 "밖"에서 publishEvent 하면?
//
// PaymentAuditService.recordAttempt() 는 @Transactional 이 없다(검증/조회처럼
// DB 트랜잭션 없는 작업). 이 상태에서 @TransactionalEventListener 가 어떻게 되나:
//   - fallbackExecution=false (기본) → 등록할 트랜잭션이 없어 "조용히 무시"
//   - fallbackExecution=true        → 트랜잭션 없으면 즉시 실행 (그냥 @EventListener 처럼)
//
// ※ 2-1/2-2/2-5 와 다른 이벤트 타입으로 교차발화 차단.
// ─────────────────────────────────────────────────────────────

record PaymentAuditEvent(Long paymentId) {}

@Service
class PaymentAuditService {
    private final ApplicationEventPublisher publisher;
    PaymentAuditService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

    // ★ @Transactional 없음 — 트랜잭션 밖에서 발행하는 케이스
    void recordAttempt(Long paymentId) {
        System.out.println("[publisher] recordAttempt — id=" + paymentId + " (트랜잭션 없음)");
        publisher.publishEvent(new PaymentAuditEvent(paymentId));
        System.out.println("[publisher] return");
    }
}

@Component
class PaymentAuditListener {

    // TODO: 1차로 아래 그대로(fallbackExecution 안 줌 = 기본 false) 돌려서
    //       리스너가 안 불리는지(조용히 무시) 확인.
    //       그 다음 fallbackExecution = true 를 추가하고 다시 돌려 즉시 실행되는지 비교.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onAudit(PaymentAuditEvent e) {
        System.out.println("  [감사] 결제 시도 기록 — id=" + e.paymentId());
    }
}

@SpringBootApplication
public class Stage2_4_FallbackExecution {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage2_4_FallbackExecution.class, args);
        var svc = ctx.getBean(PaymentAuditService.class);

        System.out.println("=== 트랜잭션 밖에서 recordAttempt(1) ===");
        svc.recordAttempt(1L);

        MeasurementLog.save("s2-4", "트랜잭션 밖 publishEvent — fallbackExecution false/true 비교");
    }
}
