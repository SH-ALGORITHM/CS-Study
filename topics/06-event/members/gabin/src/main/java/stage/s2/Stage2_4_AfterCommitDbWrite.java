package stage.s2;

import infra.MeasurementLog;
import java.math.BigDecimal;
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
 * STAGE 2-4 — AFTER_COMMIT 리스너에서 DB 쓰기 — 직접 실행으로 결과 확인.
 *
 * <h3>시나리오</h3>
 * <ol>
 *   <li>NoTxListener — 그냥 jdbc.update() 만. 본 트랜잭션은 이미 commit / 정리 → 어떤 동작?</li>
 *   <li>RequiresNewListener — @Transactional(REQUIRES_NEW) 명시. 새 트랜잭션 시작 → 정상 INSERT</li>
 * </ol>
 *
 * <h3>중요 — 결과는 환경에 따라 다름. 단정 금지</h3>
 * AFTER_COMMIT 시점은 트랜잭션이 물리적 commit 됐지만 <b>afterCompletion 전 → TransactionSynchronizationManager 동기화가 살아있음</b>.
 * 이 상태에서 JdbcTemplate.update() 호출 시:
 * <ul>
 *   <li>DataSourceUtils.getConnection() 이 새 conn 을 autoCommit=true 로 가져오는 경우 → 정상 INSERT</li>
 *   <li>트랜잭션에 묶여있던 기존 ConnectionHolder 반환하는 경우 → 그 conn 은 이미 commit + autoCommit=false → release 시 commit 없이 닫혀 <b>유실</b></li>
 * </ul>
 * 어느 쪽인지는 H2 버전 / Spring 버전 / 동기화 상태에 따라 갈림. 직접 실행해서 카운트 확인.
 *
 * <h3>JPA + Hibernate 환경 (7 주차 영역)</h3>
 * <ul>
 *   <li>EntityManager.persist() 가 영속성 컨텍스트에 보관만 → flush 는 트랜잭션 commit 시점</li>
 *   <li>본 트랜잭션은 이미 끝 → flush 안 됨 → <b>조용한 no-op 함정 진짜 발생</b></li>
 * </ul>
 *
 * <h3>결론 — 환경 무관하게 @Transactional(REQUIRES_NEW) 명시</h3>
 * 새 트랜잭션이 답. JDBC / JPA 모두 안전. RequiresNewListener 의 INSERT 는 어떤 환경에서도 반영됨.
 */
@Configuration
@EnableAutoConfiguration
public class Stage2_4_AfterCommitDbWrite {

    public record OrderCompletedEvent(Long orderId, BigDecimal amount) {}

    @Bean
    public OrderService orderService(JdbcTemplate jdbc, ApplicationEventPublisher p) {
        return new OrderService(jdbc, p);
    }

    @Bean public NoTxListener noTxListener(JdbcTemplate jdbc) { return new NoTxListener(jdbc); }
    @Bean public RequiresNewListener reqNewListener(JdbcTemplate jdbc) { return new RequiresNewListener(jdbc); }

    public static class OrderService {
        private final JdbcTemplate jdbc;
        private final ApplicationEventPublisher publisher;
        public OrderService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
            this.jdbc = jdbc;
            this.publisher = publisher;
        }

        @Transactional
        public void completeOrder(Long orderId, BigDecimal amount) {
            System.out.println("  [SERVICE] INSERT id=" + orderId);
            jdbc.update("INSERT INTO orders(id, amount) VALUES (?, ?)", orderId, amount);
            publisher.publishEvent(new OrderCompletedEvent(orderId, amount));
        }

        public Integer countOrders() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        }
    }

    public static class NoTxListener {
        private final JdbcTemplate jdbc;
        public NoTxListener(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(OrderCompletedEvent e) {
            System.out.println("    [NoTx] 주문 이력 INSERT (no @Transactional)");
            try {
                jdbc.update("INSERT INTO order_history(order_id, action) VALUES (?, ?)",
                    e.orderId(), "NOTIFIED_WITHOUT_TX");
                System.out.println("    [NoTx] update 호출 끝 (예외 없음)");
            } catch (Exception ex) {
                System.out.println("    [NoTx] 예외: " + ex.getMessage());
            }
        }
    }

    public static class RequiresNewListener {
        private final JdbcTemplate jdbc;
        public RequiresNewListener(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        @Transactional(propagation = Propagation.REQUIRES_NEW)   // ★ 새 트랜잭션
        public void on(OrderCompletedEvent e) {
            System.out.println("    [ReqNew] 주문 이력 INSERT (REQUIRES_NEW)");
            jdbc.update("INSERT INTO order_history(order_id, action) VALUES (?, ?)",
                e.orderId(), "NOTIFIED_REQUIRES_NEW");
            System.out.println("    [ReqNew] update 호출 끝");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_4_AfterCommitDbWrite.class, args);
        OrderService svc = ctx.getBean(OrderService.class);

        MeasurementLog.title("STAGE 2-4 — AFTER_COMMIT 리스너에서 DB 쓰기");

        System.out.println("  시작 orders 카운트: " + svc.countOrders());

        MeasurementLog.section("completeOrder(1, 100) — publisher INSERT + 두 리스너 호출");
        svc.completeOrder(1L, BigDecimal.valueOf(100));

        System.out.println();
        Integer historyCount = ctx.getBean(JdbcTemplate.class)
            .queryForObject("SELECT COUNT(*) FROM order_history", Integer.class);
        System.out.println("  최종 orders 카운트: " + svc.countOrders());
        System.out.println("  최종 history 카운트: " + historyCount + "  ← ReqNew 이력은 반드시 반영");
        System.out.println("    · NoTx 이력은 환경 (H2/Spring 버전 / 동기화 상태) 따라 갈림");

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · 카운트 = 3 → NoTx 도 새 conn autoCommit 으로 INSERT 됨 (운 좋은 케이스)");
        System.out.println("  · 카운트 = 2 → NoTx 가 기존 ConnectionHolder 받아서 commit 없이 닫힘 (유실 함정)");
        System.out.println("  · JPA + Hibernate (7 주차) — flush 시점 문제로 NoTx 가 조용히 무시되는 게 거의 확실");
        System.out.println("  · 결론 — 환경 무관 @Transactional(REQUIRES_NEW) 명시. ReqNew 는 어떤 환경에서도 안전");
        System.out.println("  · @Async + AFTER_COMMIT 조합은 새 스레드라 TransactionSynchronizationManager / 영속성 컨텍스트도 같이 날아감");
        MeasurementLog.record(
            "s2-4",
            "AFTER_COMMIT DB 쓰기 — history=" + historyCount
                + " / REQUIRES_NEW 이력 반영 확인"
        );
        ctx.close();
    }
}

