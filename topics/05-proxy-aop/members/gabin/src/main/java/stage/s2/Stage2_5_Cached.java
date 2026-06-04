package stage.s2;

import domain.BookCatalogService;
import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({BookCatalogService.class, domain.CacheAspect.class})
public class Stage2_5_Cached {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_5_Cached.class, args);
        BookCatalogService svc = ctx.getBean(BookCatalogService.class);

        MeasurementLog.title("STAGE 2-5 — @Cached 자작 어노테이션 적용");

        MeasurementLog.section("첫 번째 호출 — cache miss, 실제 메서드 실행");
        long firstMs = measure(() -> svc.findBookTitle(1L));

        MeasurementLog.section("두 번째 호출 — cache hit, 실제 메서드 실행 X");
        long secondMs = measure(() -> svc.findBookTitle(1L));

        MeasurementLog.section("다른 인자 호출 — cache key 가 달라서 miss");
        long otherMs = measure(() -> svc.findBookTitle(2L));

        MeasurementLog.section("결과 확인");
        MeasurementLog.row("첫 호출", firstMs + "ms");
        MeasurementLog.row("두 번째 호출", secondMs + "ms");
        MeasurementLog.row("다른 인자 호출", otherMs + "ms");
        MeasurementLog.row("실제 DB 조회 흉내 횟수", svc.dbQueryCount());

        MeasurementLog.section("학습 포인트");
        System.out.println("  · @Cached 가 붙은 메서드를 CacheAspect 가 @Around 로 가로챈다.");
        System.out.println("  · hit 이면 pjp.proceed() 를 호출하지 않으므로 실제 메서드가 실행되지 않는다.");
        System.out.println("  · cache key 에 메서드 + 인자를 넣어야 서로 다른 호출이 섞이지 않는다.");

        MeasurementLog.save("s2-5", "@Cached 자작 어노테이션 적용",
            "first=" + firstMs + "ms / second=" + secondMs
                + "ms / queryCount=" + svc.dbQueryCount());

        ctx.close();
    }

    private static long measure(Runnable runnable) {
        long start = System.nanoTime();
        runnable.run();
        return (System.nanoTime() - start) / 1_000_000;
    }
}
