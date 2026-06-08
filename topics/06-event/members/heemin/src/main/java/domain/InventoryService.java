package domain;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final ApplicationEventPublisher publisher;

    public InventoryService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void decrease(Long productId, int quantity) {

        System.out.println("[SERVICE] 재고 차감");

        publisher.publishEvent(
            new InventoryChangedEvent(
                productId,
                100,
                100 - quantity
            )
        );

        System.out.println("[SERVICE] 메서드 종료");
    }
}
