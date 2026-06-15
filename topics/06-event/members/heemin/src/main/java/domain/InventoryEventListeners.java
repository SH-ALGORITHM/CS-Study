package domain;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InventoryEventListeners {

    @Async
    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT
    )
    public void audit(
        InventoryChangedEvent event
    ) {

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }

        System.out.println(
            "[AUDIT] productId="
                + event.productId()
        );
    }

    @Async
    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT
    )
    public void notify(
        InventoryChangedEvent event
    ) {

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }

        System.out.println(
            "[NOTIFY] 재고 변경 알림"
        );
    }
}
