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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * STAGE 2-4 — AFTER_COMMIT 리스너에서 DB 쓰기 함정 시연.
 * (재고 도메인 + example 정석 구조)
 */
@Configuration
@EnableAutoConfiguration
public class Stage2_4_AfterCommitDbWrite {

    public record InventoryChangedEvent(Long id, int amount) {}

    @Bean
    public InventoryService inventoryService(JdbcTemplate jdbc, ApplicationEventPublisher p) {
        return new InventoryService(jdbc, p);
    }

    @Bean public NoTxListener noTxListener(JdbcTemplate jdbc) { return new NoTxListener(jdbc); }
    @Bean public RequiresNewListener reqNewListener(JdbcTemplate jdbc) { return new RequiresNewListener(jdbc); }

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
            jdbc.execute("CREATE TABLE IF NOT EXISTS inventory_history (id SERIAL PRIMARY KEY, inventory_id BIGINT, amount INT)");
            jdbc.update("INSERT INTO inventory (id, stock) VALUES (1, 100) ON CONFLICT DO NOTHING");
        }

        @Transactional
        public void decrement(Long id, int amount) {
            System.out.println("  [SERVICE] 재고 차감 id=" + id);
            jdbc.update("UPDATE inventory SET stock = stock - ? WHERE id = ?", amount, id);
            publisher.publishEvent(new InventoryChangedEvent(id, amount));
        }

        public Integer countHistory() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_history", Integer.class);
        }
    }

    /** 함정 리스너: @Transactional 없음 */
    public static class NoTxListener {
        private final JdbcTemplate jdbc;
        public NoTxListener(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(InventoryChangedEvent e) {
            System.out.println("    [NoTx] 히스토리 INSERT 시도 (no @Transactional)");
            jdbc.update("INSERT INTO inventory_history(inventory_id, amount) VALUES (?, ?)", e.id(), e.amount());
            System.out.println("    [NoTx] update 호출 종료");
        }
    }

    /** 해결 리스너: REQUIRES_NEW 사용 */
    public static class RequiresNewListener {
        private final JdbcTemplate jdbc;
        public RequiresNewListener(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        @Transactional(propagation = Propagation.REQUIRES_NEW)   // ★ 새 트랜잭션 시작
        public void on(InventoryChangedEvent e) {
            System.out.println("    [ReqNew] 히스토리 INSERT 시도 (REQUIRES_NEW)");
            jdbc.update("INSERT INTO inventory_history(inventory_id, amount) VALUES (?, ?)", e.id(), e.amount());
            System.out.println("    [ReqNew] update 호출 종료");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_4_AfterCommitDbWrite.class, args);
        InventoryService svc = ctx.getBean(InventoryService.class);

        MeasurementLog.title("STAGE 2-4 — AFTER_COMMIT 리스너에서의 DB 쓰기 함정");

        System.out.println("  초기 히스토리 카운트: " + svc.countHistory());

        MeasurementLog.section("decrement(1, 10) 실행");
        svc.decrement(1L, 10);

        System.out.println();
        Integer finalCount = svc.countHistory();
        MeasurementLog.row("최종 히스토리 카운트", finalCount);

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · AFTER_COMMIT 리스너는 이미 '본 트랜잭션'이 물리적으로 커밋된 후임");
        System.out.println("  · 하지만 스레드에는 여전히 이전 트랜잭션 동기화 정보가 남아있을 수 있음");
        System.out.println("  · NoTx: @Transactional 이 없으면 기존(끝난) 커넥션을 재사용하려다 commit 없이 닫혀 데이터가 '유실'될 위험이 큼");
        System.out.println("  · ReqNew: @Transactional(REQUIRES_NEW) 를 쓰면 확실히 '새 커넥션/새 트랜잭션'을 열어 안전하게 저장함");
        System.out.println("  · 결론: AFTER_COMMIT 리스너에서 DB 쓰기가 필요하다면 반드시 REQUIRES_NEW 를 사용하자");

        ctx.close();
    }
}
