package domain;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InventoryEventListeners {

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT
    )
    public void audit(
        InventoryChangedEvent event
    ) {
        System.out.println(
            "[AUDIT] productId=" + event.productId()
        );
    }

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT
    )
    public void notify(
        InventoryChangedEvent event
    ) {
        System.out.println(
            "[NOTIFY] 재고 변경 알림"
        );
    }
}
