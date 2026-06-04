package stage.s3;

import infra.MeasurementLog;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
    Stage3_1_Overhead.PlainService.class,
    Stage3_1_Overhead.AdvisedService.class,
    Stage3_1_Overhead.NoopAspect.class
})
public class Stage3_1_Overhead {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Counted {
    }

    @Service
    public static class PlainService {
        public int doWork() {
            return 42;
        }
    }

    @Service
    public static class AdvisedService {
        @Counted
        public int doWork() {
            return 42;
        }
    }

    @Aspect
    @Component
    public static class NoopAspect {
        @Around("@annotation(stage.s3.Stage3_1_Overhead$Counted)")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            return pjp.proceed();
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_1_Overhead.class, args);
        PlainService plain = ctx.getBean(PlainService.class);
        AdvisedService advised = ctx.getBean(AdvisedService.class);

        int warmup = 5_000;
        int iterations = 1_000_000;

        long sink = 0;
        for (int i = 0; i < warmup; i++) {
            sink += plain.doWork();
            sink += advised.doWork();
        }

        long t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            sink += plain.doWork();
        }
        long plainMs = (System.nanoTime() - t1) / 1_000_000;

        long t2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            sink += advised.doWork();
        }
        long advisedMs = (System.nanoTime() - t2) / 1_000_000;

        MeasurementLog.title("STAGE 3-1 — AOP 오버헤드 측정 (1M 회)");
        MeasurementLog.row("plain.doWork() 1M 회", plainMs + " ms");
        MeasurementLog.row("advised.doWork() 1M 회", advisedMs + " ms");
        String overheadPct = plainMs == 0
            ? "N/A"
            : String.format("%.1f %%", ((double) (advisedMs - plainMs) / plainMs * 100));
        MeasurementLog.row("오버헤드", (advisedMs - plainMs) + " ms (" + overheadPct + ")");
        MeasurementLog.row("회당 추가 비용",
            String.format("%.1f ns", (double) (advisedMs - plainMs) * 1_000_000 / iterations));
        MeasurementLog.row("(sink — DCE 방지)", sink);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · JIT 웜업 후 측정해야 한다.");
        System.out.println("  · advice 본문이 비어 있어도 프록시 호출 자체 비용은 생긴다.");
        System.out.println("  · 실제 캐싱에서는 캐시 조회/키 생성 비용도 오버헤드에 포함된다.");

        MeasurementLog.save("s3-1", "AOP 오버헤드 1M 회",
            "plain=" + plainMs + "ms / advised=" + advisedMs + "ms / overhead=" + overheadPct);

        ctx.close();
    }
}
