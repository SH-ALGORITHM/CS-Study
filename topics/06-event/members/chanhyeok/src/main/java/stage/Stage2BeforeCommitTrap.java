package stage;

import domain.TransferEventListeners;
import domain.TransferService;
import infra.SchemaBootstrap;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * STAGE 2 — @EventListener 한계 재현 (= 5 주차 @Audited 한계 그대로).
 *
 * <h3>시나리오</h3>
 * <ol>
 *   <li>정상 송금 — INSERT + listener 호출 + commit</li>
 *   <li>실패 송금 — INSERT + listener 호출 + 예외 → rollback (listener 는 이미 호출됨)</li>
 * </ol>
 *
 * <h3>한계 재현 포인트</h3>
 * <pre>
 * transferWithFailure(1, 2, 50):
 *   INSERT / UPDATE — DB 변경
 *   publishEvent    — listener 즉시 호출 (동기, commit 전)
 *     [AUDIT]       — 감사 기록 (= 5 주차 @Audited 와 같음)
 *     [NOTIFY]      — 알림 발송 (= 외부 시스템 회수 불가)
 *   throw RuntimeException
 *   (rollback)      — DB 만 취소. listener 는 이미 실행됨.
 * </pre>
 *
 * <h3>balance 검증</h3>
 * 두 송금 후 — 정상 송금만 반영됐어야 함:
 * <ul>
 *   <li>id=1 잔액 = 1000000 - 100 - 10 = 999890 (정상 송금만)</li>
 *   <li>id=2 잔액 = 1000000 + 100 = 1000100</li>
 *   <li>fee 누적 = 10 (정상 송금만)</li>
 * </ul>
 *
 * <h3>해결 → STAGE 3 (AFTER_COMMIT)</h3>
 *
 * <h3>실행</h3>
 * <pre>./gradlew run -PmainClass=stage.Stage2BeforeCommitTrap</pre>
 */
@SpringBootApplication(scanBasePackages = {"domain", "infra"})
public class Stage2BeforeCommitTrap {

    @Bean
    public TransferEventListeners syncListeners() {
        return new TransferEventListeners();
    }

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2BeforeCommitTrap.class, args);

        DataSource ds = ctx.getBean(DataSource.class);
        SchemaBootstrap.reset(ds);

        TransferService svc = ctx.getBean(TransferService.class);

        System.out.println();
        System.out.println("=== STAGE 2 — @EventListener 한계 (commit 전 호출 → rollback 무력) ===");

        System.out.println();
        System.out.println("--- (1) 정상 송금 transfer(1 → 2, 100) ---");
        svc.transfer(1L, 2L, new BigDecimal("100"));

        System.out.println();
        System.out.println("--- (2) 실패 송금 transferWithFailure(1 → 2, 50) — 일부러 예외 ---");
        try {
            svc.transferWithFailure(1L, 2L, new BigDecimal("50"));
        } catch (RuntimeException e) {
            System.out.println("    [caller] 예외 잡음: " + e.getMessage());
        }

        System.out.println();
        System.out.println("--- balance / fee 확인 ---");
        System.out.println("  id=1 잔액 = " + svc.balanceOf(1L) + " (예상: 999890, 정상 송금만 반영)");
        System.out.println("  id=2 잔액 = " + svc.balanceOf(2L) + " (예상: 1000100)");
        System.out.println("  fee 누적 = " + svc.feeTotal() + " (예상: 10)");

        System.out.println();
        System.out.println("[학습 포인트] — 5 주차 한계 그대로 재현");
        System.out.println("  · DB INSERT/UPDATE = rollback 으로 취소 ✓");
        System.out.println("  · [AUDIT] / [NOTIFY] = publishEvent 시점에 이미 실행됨 ✗");
        System.out.println("  · 실패 케이스에서도 사용자에게 \"송금 성공\" 알림 발송됨 (회수 불가)");
        System.out.println("  · 해결 → STAGE 3 (@TransactionalEventListener(AFTER_COMMIT))");

        ctx.close();
    }
}
