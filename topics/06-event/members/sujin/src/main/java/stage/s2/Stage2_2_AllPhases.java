package stage.s2;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

// ─────────────────────────────────────────────────────────────
// STAGE 2-2 — 4 phase 를 한 이벤트에 다 붙여서 commit/rollback 매트릭스 관찰
//
// 한 번의 complete() 가 발행한 이벤트 하나를 4개 phase 리스너가 받는다.
// 정상 commit 일 때 / 롤백일 때 각각 어떤 phase 가 찍히는지 출력으로 확인.
//
// ※ 2-1 의 PaymentCompletedEvent 와 "다른" 이벤트 타입을 써서
//    2-1 리스너(NaiveNotificationListener)와 교차발화 차단.
// ─────────────────────────────────────────────────────────────

record PaymentPhaseEvent(Long paymentId, BigDecimal amount) {}

@Service
class PaymentPhaseService {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher publisher;

    PaymentPhaseService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
        this.jdbc = jdbc;
        this.publisher = publisher;
    }

    @Transactional
    void complete(Long paymentId, BigDecimal amount) {
        jdbc.update("INSERT INTO payment(id, amount) VALUES (?, ?)", paymentId, amount);
        System.out.println("[publisher] publishEvent — id=" + paymentId);
        publisher.publishEvent(new PaymentPhaseEvent(paymentId, amount));

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("유효하지 않은 금액 (음수)");
        }
    }
}

@Component
class AllPhaseListener {

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void onBeforeCommit(PaymentPhaseEvent e) {
        System.out.println("  [BEFORE_COMMIT]    id=" + e.paymentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onAfterCommit(PaymentPhaseEvent e) {
        System.out.println("  [AFTER_COMMIT]     id=" + e.paymentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    void onAfterRollback(PaymentPhaseEvent e) {
        System.out.println("  [AFTER_ROLLBACK]   id=" + e.paymentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    void onAfterCompletion(PaymentPhaseEvent e) {
        System.out.println("  [AFTER_COMPLETION] id=" + e.paymentId());
    }
}

@SpringBootApplication
public class Stage2_2_AllPhases {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage2_2_AllPhases.class, args);
        var svc = ctx.getBean(PaymentPhaseService.class);

        System.out.println("=== 정상 케이스: complete(1, 100) ===");
        svc.complete(1L, BigDecimal.valueOf(100));

        System.out.println("\n=== 롤백 케이스: complete(2, -100) ===");
        try {
            svc.complete(2L, BigDecimal.valueOf(-100));
        } catch (Exception ex) {
            System.out.println("(rollback) " + ex.getClass().getSimpleName());
        }

        MeasurementLog.save("s2-2", "4 phase 매트릭스 — commit/rollback 별 호출 phase 관찰");
    }
}
