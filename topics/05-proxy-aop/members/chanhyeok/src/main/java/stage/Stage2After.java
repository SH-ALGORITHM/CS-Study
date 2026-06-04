package stage;

import domain.DistributedLockAspect;
import domain.TransferService;
import infra.MeasurementLog;
import infra.SchemaBootstrap;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * STAGE 2 AFTER — &#064;DistributedLock 한 줄로 추출 후.
 *
 * <h3>코드 비교</h3>
 * <table>
 *   <tr><td>Stage1Before.transferWithBoilerplate</td><td>약 25 줄 (락 인프라 24 + 비즈니스 1)</td></tr>
 *   <tr><td>TransferService.transfer</td><td>약 7 줄 (비즈니스만)</td></tr>
 * </table>
 *
 * <h3>측정 — 같은 시나리오 (50 스레드 × 200 시도)</h3>
 * Before / After 의 결과 (누락 / 락실패 / 응답시간) 가 같아야 정상.
 * → "보일러플레이트가 사라져도 동작은 동일" 시연.
 */
@SpringBootApplication(scanBasePackages = {"domain", "infra"})
public class Stage2After {

    private static final int THREADS = 50;
    private static final int ATTEMPTS = 200;
    private static final long FROM_ID = 1L;
    private static final long TO_ID = 2L;
    private static final BigDecimal AMOUNT = new BigDecimal("10");
    private static final BigDecimal INITIAL_TOTAL = BigDecimal.valueOf(2_000_000);

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2After.class, args);

        DataSource ds = ctx.getBean(DataSource.class);
        TransferService svc = ctx.getBean(TransferService.class);

        SchemaBootstrap.reset(ds);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger lockFailed = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    // ★ 한 줄 — Aspect 가 SETNX/Lua/finally 자동 처리
                    svc.transfer(FROM_ID, TO_ID, AMOUNT);
                } catch (DistributedLockAspect.LockAcquireFailedException e) {
                    lockFailed.incrementAndGet();
                }
            });
        }

        Thread.sleep(50);
        long t0 = System.nanoTime();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(300, TimeUnit.SECONDS);
        double millis = (System.nanoTime() - t0) / 1_000_000.0;

        BigDecimal total = svc.balanceOf(FROM_ID)
            .add(svc.balanceOf(TO_ID))
            .add(svc.feeTotal());
        int misses = total.compareTo(INITIAL_TOTAL) == 0 ? 0 : 1;

        System.out.println();
        System.out.println("=== STAGE 2 AFTER — @DistributedLock 한 줄 ===");
        System.out.printf("누락 %d / 락실패 %d / 응답 %.1f ms%n", misses, lockFailed.get(), millis);
        System.out.println("(TransferService.transfer 본문 라인 수 — 약 7 줄. 비즈니스만)");
        System.out.println();
        System.out.println("[비교] Stage1Before vs Stage2After 결과가 동일해야 함");
        System.out.println("  · 누락 0 / 락실패 비슷한 수 / 응답시간 비슷");
        System.out.println("  · → 보일러플레이트가 사라져도 동작 동일 = AOP 의 가치");

        MeasurementLog.save("s2-after", "@DistributedLock", misses, lockFailed.get(), millis);

        ctx.close();
    }
}
