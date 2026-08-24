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
 * STAGE 2-2 — @TransactionalEventListener 의 4가지 Phase 확인.
 * (재고 도메인 + example 정석 구조)
 */
@Configuration
@EnableAutoConfiguration
public class Stage2_2_AllPhases {

    public record InventoryEvent(Long id, int amount) {}

    @Bean
    public InventoryService inventoryService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
        return new InventoryService(jdbc, publisher);
    }

    @Bean
    public AllPhaseListener listener() { return new AllPhaseListener(); }

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
        public void changeStock(Long id, int amount) {
            System.out.println("  [SERVICE] 재고 작업 시작 id=" + id);
            jdbc.update("UPDATE inventory SET stock = stock + ? WHERE id = ?", amount, id);
            
            System.out.println("  [SERVICE] publishEvent");
            publisher.publishEvent(new InventoryEvent(id, amount));

            if (amount == 0) throw new RuntimeException("변경량이 0이라 실패");
            System.out.println("  [SERVICE] 메서드 종료 → commit 예정");
        }
    }

    public static class AllPhaseListener {
        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        public void onBefore(InventoryEvent e) { System.out.println("    [BEFORE_COMMIT] id=" + e.id()); }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onAfterCommit(InventoryEvent e) { System.out.println("    [AFTER_COMMIT]  id=" + e.id()); }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
        public void onAfterRollback(InventoryEvent e) { System.out.println("    [AFTER_ROLLBACK] id=" + e.id()); }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
        public void onAfterCompletion(InventoryEvent e) { System.out.println("    [AFTER_COMPLETION] id=" + e.id()); }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_2_AllPhases.class, args);
        InventoryService svc = ctx.getBean(InventoryService.class);

        MeasurementLog.title("STAGE 2-2 — 4 Phase 관찰 (재고 도메인)");

        MeasurementLog.section("정상 커밋 (amount=10)");
        svc.changeStock(1L, 10);

        System.out.println();
        MeasurementLog.section("롤백 상황 (amount=0)");
        try { svc.changeStock(1L, 0); }
        catch (Exception ignored) {}

        ctx.close();
    }
}
