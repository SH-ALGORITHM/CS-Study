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
 * STAGE 1 — 본인 도메인 첫 진입.
 *
 * <h3>5 주차 → 6 주차 가장 작은 변경</h3>
 * <ul>
 *   <li>TransferService.@Audited 제거 + @Transactional 적용</li>
 *   <li>transfer() 끝에 publishEvent(TransferCompletedEvent)</li>
 *   <li>TransferEventListeners — @EventListener 두 개 (audit + notify) 동기</li>
 * </ul>
 *
 * <h3>예상 출력 순서</h3>
 * <pre>
 * [TX] begin
 *   [LOCK] begin — wallet:1
 *     (실제 송금)
 *     [AUDIT]  ← publishEvent 직후 즉시 (commit 전, 동기, 같은 스레드)
 *     [NOTIFY] ← 같은 시점
 *   [LOCK] release
 * [TX] commit
 * </pre>
 *
 * <h3>이 시점의 한계 — 5 주차와 동일</h3>
 * listener 가 commit 전 호출 → rollback 시 audit / notify 이미 실행됨.
 * STAGE 2 에서 @TransactionalEventListener(AFTER_COMMIT) 으로 옮겨 해결.
 *
 * <h3>실행</h3>
 * <pre>./gradlew run -PmainClass=stage.Stage1Hello</pre>
 */
@SpringBootApplication(scanBasePackages = {"domain", "infra"})
public class Stage1Hello {

    @Bean
    public TransferEventListeners syncListeners() {
        return new TransferEventListeners();
    }

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1Hello.class, args);

        DataSource ds = ctx.getBean(DataSource.class);
        SchemaBootstrap.reset(ds);

        TransferService svc = ctx.getBean(TransferService.class);

        System.out.println();
        System.out.println("=== STAGE 1 — TransferService + publishEvent + @EventListener 동기 ===");
        System.out.println();

        System.out.println("--- transfer(1 → 2, 100) 정상 송금 ---");
        svc.transfer(1L, 2L, new BigDecimal("100"));

        System.out.println();
        System.out.println("--- balance / fee 확인 ---");
        System.out.println("  id=1 잔액 = " + svc.balanceOf(1L));
        System.out.println("  id=2 잔액 = " + svc.balanceOf(2L));
        System.out.println("  fee 누적 = " + svc.feeTotal());

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · publisher (transfer) + listener (audit/notify) 같은 스레드 = 동기");
        System.out.println("  · listener 가 publishEvent 직후 즉시 실행 — commit 전");
        System.out.println("  · 5 주차 @Audited 한계와 동일 — STAGE 2 에서 AFTER_COMMIT 으로 해결");

        ctx.close();
    }
}
