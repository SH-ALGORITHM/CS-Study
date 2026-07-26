package domain;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * STAGE 3 — @TransactionalEventListener(AFTER_COMMIT) — commit 후만 호출.
 *
 * <h3>STAGE 2 (동기) 와의 차이</h3>
 * <ul>
 *   <li>publishEvent 시점 — 콜백 등록만 (listener 호출 X)</li>
 *   <li>commit 성공 → listener 호출</li>
 *   <li>rollback → listener 호출 안 됨 (자동 스킵) ✓</li>
 * </ul>
 *
 * <h3>5 주차 → 6 주차 한계 해결의 본질</h3>
 * 트랜잭션 결과 기반 자동 분기 — 외부 호출이 트랜잭션 commit 보장 후에만 실행.
 *
 * <h3>등록</h3>
 * @Component 없음 — STAGE 3 의 @SpringBootApplication 에서 명시적 @Bean 으로 등록.
 */
public class TransferAfterCommitListeners {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAudit(TransferCompletedEvent e) {
        System.out.println("    [AUDIT-AC] " + e.fromId() + " → " + e.toId()
            + " amount=" + e.amount()
            + " at=" + e.completedAt()
            + " thread=" + Thread.currentThread().getName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotify(TransferCompletedEvent e) {
        System.out.println("    [NOTIFY-AC] 수신자 " + e.toId() + " 알림 발송"
            + " thread=" + Thread.currentThread().getName());
    }
}
