package domain;

import org.springframework.context.event.EventListener;

/**
 * STAGE 1/2 — 동기 @EventListener (= 5 주차 한계 그대로).
 *
 * <h3>특징</h3>
 * <ul>
 *   <li>publishEvent 시점에 즉시 호출 (commit 전, 동기)</li>
 *   <li>rollback 시 이미 실행된 후 — 외부 호출 회수 불가</li>
 *   <li>= 5 주차 @Audited 와 정확히 같은 한계</li>
 * </ul>
 *
 * <h3>등록</h3>
 * @Component 없음 — STAGE 1/2 의 @SpringBootApplication 에서 명시적 @Bean 으로 등록.
 * STAGE 3 (TransferAfterCommitListeners) 와의 독립 보장.
 */
public class TransferEventListeners {

    @EventListener
    public void onAudit(TransferCompletedEvent e) {
        System.out.println("    [AUDIT] " + e.fromId() + " → " + e.toId()
            + " amount=" + e.amount()
            + " at=" + e.completedAt()
            + " thread=" + Thread.currentThread().getName());
    }

    @EventListener
    public void onNotify(TransferCompletedEvent e) {
        System.out.println("    [NOTIFY] 수신자 " + e.toId() + " 알림 발송"
            + " thread=" + Thread.currentThread().getName());
    }
}
