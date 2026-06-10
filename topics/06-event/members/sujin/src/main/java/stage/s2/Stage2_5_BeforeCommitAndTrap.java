package stage.s2;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

// ─────────────────────────────────────────────────────────────
// STAGE 2-5 — BEFORE_COMMIT(같은 트랜잭션 쓰기) + AFTER_COMMIT DB쓰기 함정
//
// 실험 1: ReceiptListener(BEFORE_COMMIT) 가 receipt 테이블에 INSERT.
//         본 트랜잭션과 같이 commit 되는지 확인 (행 수로).
// 실험 2: PointListener(AFTER_COMMIT) 가 point 테이블에 INSERT.
//         REQUIRES_NEW 없이 → 트랜잭션이 이미 끝난 시점이라 조용히 유실될 수 있음.
//         → 함정 확인 후 REQUIRES_NEW 로 고친다.
//
// ※ 2-1/2-2 와 다른 이벤트 타입(PaymentSettledEvent)으로 교차발화 차단.
// ─────────────────────────────────────────────────────────────

record PaymentSettledEvent(Long paymentId, BigDecimal amount) {}

@Service
class PaymentSettleService {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher publisher;

    PaymentSettleService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
        this.jdbc = jdbc;
        this.publisher = publisher;
    }

    @Transactional
    void settle(Long paymentId, BigDecimal amount) {
        jdbc.update("INSERT INTO payment(id, amount) VALUES (?, ?)", paymentId, amount);
        publisher.publishEvent(new PaymentSettledEvent(paymentId, amount));
    }
}

// 실험 1 — BEFORE_COMMIT: 같은 트랜잭션 안에서 영수증 INSERT
@Component
class ReceiptListener {
    private final JdbcTemplate jdbc;
    ReceiptListener(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void issueReceipt(PaymentSettledEvent e) {
        jdbc.update("INSERT INTO receipt(payment_id, issued_at) VALUES (?, CURRENT_TIMESTAMP)", e.paymentId());
        System.out.println("  [BEFORE_COMMIT] 영수증 발급 — id=" + e.paymentId());
    }
}

// 실험 2 — AFTER_COMMIT: 포인트 적립 INSERT (함정 자리)
@Component
class PointListener {
    private final JdbcTemplate jdbc;
    PointListener(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // 처음엔 REQUIRES_NEW "없이" 그대로 돌려서 point 행이 남는지 확인.
    // 유실(0행)이 재현되면, 이 메서드에 새 트랜잭션을 여는 어노테이션을 추가.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void accruePoint(PaymentSettledEvent e) {
        jdbc.update("INSERT INTO point(payment_id, amount) VALUES (?, ?)", e.paymentId(), e.amount());
        System.out.println("  [AFTER_COMMIT] 포인트 적립 — id=" + e.paymentId());
    }
}

@SpringBootApplication
public class Stage2_5_BeforeCommitAndTrap {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage2_5_BeforeCommitAndTrap.class, args);
        var svc = ctx.getBean(PaymentSettleService.class);
        var jdbc = ctx.getBean(JdbcTemplate.class);

        System.out.println("=== settle(1, 100) ===");
        svc.settle(1L, BigDecimal.valueOf(100));

        Integer receiptCount = jdbc.queryForObject("SELECT COUNT(*) FROM receipt", Integer.class);
        Integer pointCount = jdbc.queryForObject("SELECT COUNT(*) FROM point", Integer.class);
        System.out.println("\nreceipt 행 수 = " + receiptCount + "  (기대 1 — BEFORE_COMMIT 은 같은 트랜잭션)");
        System.out.println("point   행 수 = " + pointCount + "  (REQUIRES_NEW 없으면 0 일 수 있음 = 함정)");

        MeasurementLog.save("s2-5", "BEFORE_COMMIT 같은 트랜잭션 쓰기 + AFTER_COMMIT DB쓰기 함정/REQUIRES_NEW");
    }
}
