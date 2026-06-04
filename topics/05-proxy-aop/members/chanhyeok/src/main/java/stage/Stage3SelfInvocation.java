package stage;

import domain.BatchTransferService;
import domain.SelfInvocationDemoService;
import infra.SchemaBootstrap;
import java.math.BigDecimal;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * STAGE 3 — 본인 도메인에서 self-invocation 함정 재현.
 *
 * <h3>두 시나리오 비교</h3>
 * <ol>
 *   <li>SelfInvocationDemoService.outerNoLock() → this.innerTransfer() 호출 → [LOCK] 출력 X (함정)</li>
 *   <li>BatchTransferService.batchTransfer() → 외부 주입 transferSvc.transfer() → [LOCK] 출력 O (정상)</li>
 * </ol>
 *
 * <h3>핵심</h3>
 * Spring CGLIB 위임 구조 — 원본 메서드 안의 {@code this} 는 원본 객체.
 * 같은 클래스 안 메서드 호출은 프록시 우회 → @DistributedLock 무시.
 */
@SpringBootApplication(scanBasePackages = {"domain", "infra"})
public class Stage3SelfInvocation {

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3SelfInvocation.class, args);

        DataSource ds = ctx.getBean(DataSource.class);
        SchemaBootstrap.reset(ds);

        SelfInvocationDemoService demo = ctx.getBean(SelfInvocationDemoService.class);
        BatchTransferService batch = ctx.getBean(BatchTransferService.class);

        System.out.println();
        System.out.println("=== STAGE 3 — self-invocation 함정 본인 도메인 ===");
        System.out.println();

        System.out.println("--- (1) self-invocation 함정: outer → this.inner() ---");
        System.out.println("demo.getClass() (외부에서 본 것) = " + demo.getClass().getName());
        demo.outerNoLock(1L, 2L, new BigDecimal("10"));

        System.out.println();
        System.out.println("--- (2) 해결책 (c) 클래스 분리: BatchTransferService → transferSvc.transfer() ---");
        batch.batchTransfer(List.of(new long[]{1L, 2L}, new long[]{1L, 2L}), new BigDecimal("10"));

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · 시나리오 (1): [outer] this.getClass() = 원본 / [inner] [LOCK] 없음 → 락 우회");
        System.out.println("  · 시나리오 (2): 매 transfer 마다 [LOCK] begin/release → 정상");
        System.out.println("  · Spring CGLIB 는 위임 방식 — 원본 안 this 는 원본 인스턴스");
        System.out.println("  · 면접 단골: @Transactional 안 먹는 4 가지 중 self-invocation");

        ctx.close();
    }
}
