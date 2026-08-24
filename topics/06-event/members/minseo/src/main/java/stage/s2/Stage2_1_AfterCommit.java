package stage.s2;

import infra.MeasurementLog;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * STAGE 2-1 — @TransactionalEventListener(AFTER_COMMIT) 해결책 시연.
 * (재고 도메인 + example 정석 구조)
 */
@Configuration
@EnableAutoConfiguration
public class Stage2_1_AfterCommit {

    public record InventoryEvent(Long id, int amount) {}

    @Bean
    public InventoryService inventoryService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
        return new InventoryService(jdbc, publisher);
    }

    @Bean
    public InventoryEventListener listener() { return new InventoryEventListener(); }

    public static class InventoryService {
        private final JdbcTemplate jdbc;
        private final ApplicationEventPublisher publisher;

        public InventoryService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
            this.jdbc = jdbc;
            this.publisher = publisher;
        }

        @PostConstruct
        public void init() {
            jdbc.execute("CREATE TABLE IF NOT EXISTS inventory (id BIGINT PRIMARY KEY, stock INT)");
            jdbc.update("INSERT INTO inventory (id, stock) VALUES (1, 100) ON CONFLICT DO NOTHING");
        }

        @Transactional
        public void decrementSuccess(Long id, int amount) {
            System.out.println("  [SERVICE] 재고 차감(성공) id=" + id);
            jdbc.update("UPDATE inventory SET stock = stock - ? WHERE id = ?", amount, id);
            publisher.publishEvent(new InventoryEvent(id, amount));
        }

        @Transactional
        public void decrementWithFailure(Long id, int amount) {
            System.out.println("  [SERVICE] 재고 차감(실패) id=" + id);
            jdbc.update("UPDATE inventory SET stock = stock - ? WHERE id = ?", amount, id);
            publisher.publishEvent(new InventoryEvent(id, amount));
            throw new RuntimeException("의도적 롤백");
        }

        public int getStock(Long id) {
            return jdbc.queryForObject("SELECT stock FROM inventory WHERE id = ?", Integer.class, id);
        }
    }

    public static class InventoryEventListener {
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onAfterCommit(InventoryEvent e) {
            System.out.println("    [AFTER_COMMIT] ✅ 알림 발송 (id=" + e.id() + ")");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_1_AfterCommit.class, args);
        InventoryService svc = ctx.getBean(InventoryService.class);

        MeasurementLog.title("STAGE 2-1 — AFTER_COMMIT (재고 도메인)");

        MeasurementLog.section("정상 커밋 상황");
        svc.decrementSuccess(1L, 10);
        MeasurementLog.row("현재 재고", svc.getStock(1L));

        System.out.println();
        MeasurementLog.section("롤백 상황 (알림 로그가 찍히지 않아야 함)");
        try { svc.decrementWithFailure(1L, 10); }
        catch (Exception ignored) {}
        MeasurementLog.row("현재 재고 (롤백되어 90 유지)", svc.getStock(1L));

        ctx.close();
    }
}
