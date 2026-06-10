package stage.s2;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

// ─────────────────────────────────────────────────────────────
// STAGE 2-1 — 순진한 버전: 그냥 @EventListener (일부러 함정 재현)
//
// 목표: 트랜잭션이 롤백돼도 리스너가 "이미" 실행돼버리는 걸 직접 본다.
// 5주차 advice 안-밖(@Audited 가 commit 전 실행)의 한계를 코드로 재현하는 자리.
// → 함정을 눈으로 본 뒤, 2-1 Step 3 에서 @TransactionalEventListener 로 고친다.
// ─────────────────────────────────────────────────────────────

// (1) 이벤트 — 과거형 이름, payload-only record. 완성본.
record PaymentCompletedEvent(Long paymentId, BigDecimal amount) {}

// (2) Publisher — JdbcTemplate(INSERT) + ApplicationEventPublisher(발행)
@Service
class PaymentService {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher publisher;

    PaymentService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
        this.jdbc = jdbc;
        this.publisher = publisher;
    }

    @Transactional
    void complete(Long paymentId, BigDecimal amount) {
        jdbc.update("INSERT INTO payment(id, amount) VALUES (?, ?)", paymentId, amount);

        // 결제 완료 이벤트 발행
        // 이 한 줄이 "명시적 발행" — AOP 가 자동으로 가로채던 것과 대비된다.
        publisher.publishEvent(new PaymentCompletedEvent(paymentId, amount));

        // 금액이 음수면 예외를 던져 롤백을 유발
        // INSERT 는 이미 했고 이벤트도 이미 발행한 "후"라는 게 함정의 핵심
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("유효하지 않은 금액 (음수)");
        }
    }
}

// (3) Listener — 순진한 @EventListener (트랜잭션 무관). 이게 함정.
@Component
class NaiveNotificationListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(PaymentCompletedEvent e) {
        // 실제로는 이메일/슬랙/SMS — 한 번 전송하면 회수 불가
        System.out.println("[알림] 결제 완료 전송 — id=" + e.paymentId() + " / amount=" + e.amount());
    }
}

// (4) main — 정상 케이스 1번 + 롤백 케이스 1번 실행하고 payment 행 수 확인
@SpringBootApplication
public class Stage2_1_NaiveEventListener {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage2_1_NaiveEventListener.class, args);
        var svc = ctx.getBean(PaymentService.class);
        var jdbc = ctx.getBean(JdbcTemplate.class);

        System.out.println("=== 정상 케이스: complete(1, 100) ===");
        svc.complete(1L, BigDecimal.valueOf(100));

        System.out.println("\n=== 롤백 케이스: complete(2, -100) ===");
        try {
            svc.complete(2L, BigDecimal.valueOf(-100));
        } catch (Exception ex) {
            System.out.println("(rollback) " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }

        // payment 테이블에 실제로 몇 행이 남았는지 확인.
        // 기대: 롤백됐으니 1행. 하지만 위 알림은 2번 출력됐을 것이다 — 그게 함정.
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM payment", Integer.class);
        System.out.println("\npayment 행 수 = " + count + "  (기대 1)");

        MeasurementLog.save("s2-1", "순진한 @EventListener — 롤백돼도 알림 이미 전송됨(함정 재현)");
    }
}
