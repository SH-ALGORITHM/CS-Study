package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

// ─────────────────────────────────────────────────────────────
// STAGE 3-6 — @Async + AFTER_COMMIT = 완전히 새 스레드 함정
//
// AFTER_COMMIT 콜백이 @Async 로 별 스레드에서 실행되면:
//   - TransactionSynchronizationManager (ThreadLocal, 5주차 회수) → 날아감
//   - 영속성 컨텍스트(7주차 JPA) → 날아감 → Lazy 로딩 시 LazyInitializationException
//   - 활성 트랜잭션 없음
//
// Run A: REQUIRES_NEW 없이 → actualTxActive=false / syncActive=false (컨텍스트 없음)
// Run B: @Transactional(REQUIRES_NEW) 추가 → actualTxActive=true (새 트랜잭션)
// ─────────────────────────────────────────────────────────────

record PaymentAsyncEvent(Long paymentId, BigDecimal amount) {}

@Service
class PaymentAsyncService {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher publisher;

    PaymentAsyncService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
        this.jdbc = jdbc;
        this.publisher = publisher;
    }

    @Transactional
    void settle(Long paymentId, BigDecimal amount) {
        jdbc.update("INSERT INTO payment(id, amount) VALUES (?, ?)", paymentId, amount);
        System.out.println("[settle] thread=" + Thread.currentThread().getName()
                + " actualTxActive=" + TransactionSynchronizationManager.isActualTransactionActive());
        publisher.publishEvent(new PaymentAsyncEvent(paymentId, amount));
    }
}

@Component
class AsyncAfterCommitListener {
    private final JdbcTemplate jdbc;
    AsyncAfterCommitListener(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onAfterCommit(PaymentAsyncEvent e) {
        System.out.println("  [async-after-commit] thread=" + Thread.currentThread().getName());
        System.out.println("    actualTxActive=" + TransactionSynchronizationManager.isActualTransactionActive());
        System.out.println("    syncActive="     + TransactionSynchronizationManager.isSynchronizationActive());
        jdbc.update("INSERT INTO point(payment_id, amount) VALUES (?, ?)", e.paymentId(), e.amount());
        System.out.println("    point INSERT 시도 완료");
    }
}

@SpringBootApplication
@EnableAsync
public class Stage3_6_AsyncAfterCommit {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage3_6_AsyncAfterCommit.class, args);
        var svc = ctx.getBean(PaymentAsyncService.class);

        System.out.println("=== settle(1, 100) ===");
        svc.settle(1L, BigDecimal.valueOf(100));

        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        MeasurementLog.save("s3-6", "@Async + AFTER_COMMIT 새 스레드 — 트랜잭션 컨텍스트 유무");
    }
}
