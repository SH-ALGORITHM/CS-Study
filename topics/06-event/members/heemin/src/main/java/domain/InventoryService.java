package domain;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final ApplicationEventPublisher publisher;
    private final JdbcTemplate jdbcTemplate;

    public InventoryService(
        ApplicationEventPublisher publisher,
        JdbcTemplate jdbcTemplate
    ) {
        this.publisher = publisher;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void decrease(
        Long productId,
        int quantity
    ) {

        System.out.println(
            "[SERVICE] 재고 차감 시작"
        );

        jdbcTemplate.update(
            """
            update inventory
               set quantity = quantity - ?
             where product_id = ?
            """,
            quantity,
            productId
        );

        publisher.publishEvent(
            new InventoryChangedEvent(
                productId,
                100,
                100 - quantity
            )
        );

        System.out.println(
            "[SERVICE] 이벤트 발행 완료"
        );
    }
}
