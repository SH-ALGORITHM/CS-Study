package stage;

import domain.TransferAfterCommitListeners;
import domain.TransferService;
import infra.SchemaBootstrap;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * STAGE 3 — @TransactionalEventListener(AFTER_COMMIT) 해결.
 *
 * <h3>STAGE 2 와 같은 시나리오, 다른 listener</h3>
 * <ul>
 *   <li>(1) 정상 송금 — commit 성공 → listener 호출 ✓</li>
 *   <li>(2) 실패 송금 — rollback → listener 호출 안 됨 ✓ (= STAGE 2 한계 해결)</li>
 * </ul>
 *
 * <h3>STAGE 2 vs STAGE 3 출력 비교</h3>
 * <pre>
 * STAGE 2 (동기 @EventListener):
 *   (1) 정상   [AUDIT] [NOTIFY] (commit 전, 동기)
 *   (2) 실패   [AUDIT] [NOTIFY] (commit 전, rollback 돼도 이미 호출) ✗
 *
 * STAGE 3 (@TransactionalEventListener(AFTER_COMMIT)):
 *   (1) 정상   [AUDIT-AC] [NOTIFY-AC] (commit 후 호출)
 *   (2) 실패   (listener 호출 X — 외부 호출 안전) ✓
 * </pre>
 *
 * <h3>실행</h3>
 * <pre>./gradlew run -PmainClass=stage.Stage3AfterCommit</pre>
 */
@SpringBootApplication(scanBasePackages = {"domain", "infra"})
public class Stage3AfterCommit {

    @Bean
    public TransferAfterCommitListeners afterCommitListeners() {
        return new TransferAfterCommitListeners();
    }

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3AfterCommit.class, args);

        DataSource ds = ctx.getBean(DataSource.class);
        SchemaBootstrap.reset(ds);

        TransferService svc = ctx.getBean(TransferService.class);

        System.out.println();
        System.out.println("=== STAGE 3 — @TransactionalEventListener(AFTER_COMMIT) 해결 ===");

        System.out.println();
        System.out.println("--- (1) 정상 송금 transfer(1 → 2, 100) ---");
        svc.transfer(1L, 2L, new BigDecimal("100"));

        System.out.println();
        System.out.println("--- (2) 실패 송금 transferWithFailure(1 → 2, 50) — 일부러 예외 ---");
        try {
            svc.transferWithFailure(1L, 2L, new BigDecimal("50"));
        } catch (RuntimeException e) {
            System.out.println("    [caller] 예외 잡음: " + e.getMessage());
            System.out.println("    ← listener (AC) 호출 안 됨 (commit 안 됐으므로)");
        }

        System.out.println();
        System.out.println("--- balance / fee 확인 ---");
        System.out.println("  id=1 잔액 = " + svc.balanceOf(1L) + " (예상: 999890, 정상 송금만 반영)");
        System.out.println("  id=2 잔액 = " + svc.balanceOf(2L) + " (예상: 1000100)");
        System.out.println("  fee 누적 = " + svc.feeTotal() + " (예상: 10)");

        System.out.println();
        System.out.println("[학습 포인트] — 5 주차 한계 해결");
        System.out.println("  · 정상 commit → [AUDIT-AC] / [NOTIFY-AC] 호출 ✓");
        System.out.println("  · rollback   → listener 호출 X ✓ — 외부 호출 안전");
        System.out.println("  · 변경점은 어노테이션 한 줄 (@EventListener → @TransactionalEventListener(AFTER_COMMIT))");
        System.out.println("  · STAGE 2 의 같은 시나리오와 출력 직접 비교");

        ctx.close();
    }
}
