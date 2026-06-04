package stage;

import domain.TransferService;
import infra.SchemaBootstrap;
import java.math.BigDecimal;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * STAGE 4 — 본인 도메인에서 양파 껍질 (@Order 체이닝).
 *
 * <h3>적용된 advice</h3>
 * <ul>
 *   <li>@Order(1) DistributedLockAspect — 가장 바깥</li>
 *   <li>@Order(2) AuditAspect — 안쪽</li>
 * </ul>
 *
 * <h3>예상 출력</h3>
 * <pre>
 * [LOCK] begin — wallet:1
 *   [AUDIT] before — action=TRANSFER method=transfer args=[1, 2, 10]
 *      (실제 송금)
 *   [AUDIT] success (Xms)
 * [LOCK] release
 * </pre>
 *
 * <h3>주의</h3>
 * AUDIT 가 LOCK 안쪽 → commit 전에 감사 기록 발사. 만약 audit 이 외부 알림 / 다른 시스템 호출이면
 * commit 후로 옮겨야 (6 주차 @TransactionalEventListener(AFTER_COMMIT)).
 */
@SpringBootApplication(scanBasePackages = {"domain", "infra"})
public class Stage4AspectChaining {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4AspectChaining.class, args);

        DataSource ds = ctx.getBean(DataSource.class);
        SchemaBootstrap.reset(ds);

        TransferService svc = ctx.getBean(TransferService.class);

        System.out.println();
        System.out.println("=== STAGE 4 — @Order 양파 껍질 (@DistributedLock + @Audited) ===");
        System.out.println();

        System.out.println("--- transfer(1 → 2, 100) 정상 송금 ---");
        svc.transfer(1L, 2L, new BigDecimal("100"));

        System.out.println();
        System.out.println("--- transfer(1 → 2, 10) 추가 송금 (락 재획득 확인) ---");
        svc.transfer(1L, 2L, new BigDecimal("10"));

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · @Order(1) DistributedLockAspect 가장 바깥 → 락 잡은 후 audit 실행");
        System.out.println("  · @Order(2) AuditAspect 안쪽 → 락 보호 안에서 감사 기록");
        System.out.println("  · TX 가 가장 바깥인 패턴과 동일 — commit 전 처리는 6 주차 @TransactionalEventListener 로 보완");

        ctx.close();
    }
}
