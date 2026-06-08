package domain;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventListeners {

    @EventListener
    public void audit(InventoryChangedEvent event) {

        System.out.println(
            "[AUDIT] productId=" + event.productId()
        );
    }

    @EventListener
    public void notifyInventory(InventoryChangedEvent event) {

        System.out.println(
            "[NOTIFY] 재고 변경 알림"
        );
    }
}
