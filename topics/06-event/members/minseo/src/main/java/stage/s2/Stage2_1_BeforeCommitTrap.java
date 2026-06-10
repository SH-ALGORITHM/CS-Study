package stage.s2;

import infra.MeasurementLog;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication(scanBasePackages = {"infra"})
public class Stage2_1_BeforeCommitTrap {

    // 1. 이벤트 정의 (재고 변경 사실을 담음)
    public record InventoryChangedEvent(Long id, int amount) {
    }

    @Bean
    public InventoryEventListener inventoryEventListener() {
        return new InventoryEventListener();
    }

    @Service
    public static class InventoryService {
        private final JdbcTemplate jdbc;
        private final ApplicationEventPublisher publisher;

        public InventoryService(JdbcTemplate jdbc, ApplicationEventPublisher publisher) {
            this.jdbc = jdbc;
            this.publisher = publisher;
        }

        @PostConstruct
        public void init() {
            // 테스트용 테이블 및 데이터 준비
            jdbc.execute("CREATE TABLE IF NOT EXISTS inventory (id BIGINT PRIMARY KEY, stock INT)");
            jdbc.update("INSERT INTO inventory (id, stock) VALUES (1, 100) ON CONFLICT DO NOTHING");
        }

        @Transactional
        public void decrement(Long id, int amount) {
            System.out.println("[Service] 재고 차감 시작 — id=" + id);

            // (A) DB 업데이트: 재고 감소
            jdbc.update("UPDATE inventory SET stock = stock - ? WHERE id = ?", amount, id);

            // (B) 이벤트 발행: "재고가 줄어들었으니 알림을 보내라!"
            publisher.publishEvent(new InventoryChangedEvent(id, amount));

            // (C) 일부러 예외 발생! -> 트랜잭션 롤백 유도
            System.out.println("[Service] 💥 의도적 예외 발생 (롤백 예정)");
            throw new RuntimeException("DB 장애 발생으로 롤백됨!");
        }

        public int getStock(Long id) {
            return jdbc.queryForObject("SELECT stock FROM inventory WHERE id = ?", Integer.class, id);
        }
    }

    public static class InventoryEventListener {
        @EventListener // ★ 일반 이벤트 리스너 (트랜잭션 무관)
        public void onInventoryChanged(InventoryChangedEvent event) {
            System.out.println("  [Listener] 🔔 알림 발송 완료: 재고 변경됨(id=" + event.id() + ")");
        }
    }

    // 메인 로직은 제가 실행 결과를 관찰하기 좋게 구성해 드릴게요.
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_1_BeforeCommitTrap.class, args);
        InventoryService service = ctx.getBean(InventoryService.class);

        MeasurementLog.title("STAGE 2-1 — 트랜잭션 롤백 함정 시연");

        try {
            service.decrement(1L, 10);
        } catch (Exception e) {
            System.out.println("[Main] 예외 캐치: " + e.getMessage());
        }

        // 결과 확인: DB 재고가 정말 롤백되었는지 확인
        int finalStock = service.getStock(1L);
        MeasurementLog.row("최종 재고 (100에서 10 차감 시도 후 롤백)", finalStock);

        System.out.println("\n[관찰 포인트]");
        System.out.println("  1. DB 재고는 100으로 유지되었는가? (롤백 성공 여부)");
        System.out.println("  2. '🔔 알림 발송 완료' 로그가 찍혔는가? (이벤트 실행 여부)");

        ctx.close();
    }
}
