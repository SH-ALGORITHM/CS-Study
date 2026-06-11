package domain;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventListener {

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT
    )
    public void onAudit(
        AuditEvent event
    ) {

        System.out.println(
            "[AUDIT] productId="
                + event.productId()
                + ", quantity="
                + event.quantity()
        );
    }
}
