package stage.s3;

import infra.MeasurementLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * STAGE 3-1 — AOP 적용 전후 응답 시간 (오버헤드 측정).
 *
 * <p>웜업 5,000 회 후 본 측정 1,000,000 회. JIT 컴파일 안정화 후의 실제 비용 측정.
 */
@Configuration
@EnableAutoConfiguration
@org.springframework.context.annotation.ComponentScan(
    basePackages = "stage.s3",
    excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = {Stage3_3_GetClass.class, Stage3_4_BeanPostProcessors.class}
    )
)
public class Stage3_1_Overhead {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Counted {}

    @Service
    public static class PlainService {
        public int doWork() { return 42; }
    }

    @Service
    public static class AdvisedService {
        @Counted
        public int doWork() { return 42; }
    }

    @Aspect
    @Component
    public static class NoopAspect {
        // Inner class 어노테이션은 $ 구분자 — AspectJ 파서 호환성 (Stage2_4 와 통일)
        @Around("@annotation(stage.s3.Stage3_1_Overhead$Counted)")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            return pjp.proceed();   // 아무것도 안 함 — 순수 프록시 오버헤드만 측정
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_1_Overhead.class, args);
        PlainService plain = ctx.getBean(PlainService.class);
        AdvisedService advised = ctx.getBean(AdvisedService.class);

        int warmup = 5_000;
        int iterations = 1_000_000;

        // JIT 웜업
        long warmupSink = 0;
        for (int i = 0; i < warmup; i++) {
            warmupSink += plain.doWork();
            warmupSink += advised.doWork();
        }

        // sink 누적으로 DCE (dead code elimination) 방지 — 결과 안 쓰면 JIT 가 호출 자체 제거 가능
        long sink = warmupSink;
        long t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) sink += plain.doWork();
        long plainMs = (System.nanoTime() - t1) / 1_000_000;

        long t2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) sink += advised.doWork();
        long advisedMs = (System.nanoTime() - t2) / 1_000_000;
        System.out.println("  (sink = " + sink + ")");   // DCE 방지 — 출력으로 사용 강제

        MeasurementLog.title("STAGE 3-1 — AOP 오버헤드 측정 (1M 회)");
        MeasurementLog.row("plain.doWork() 1M 회", plainMs + " ms");
        MeasurementLog.row("advised.doWork() 1M 회", advisedMs + " ms");
        // plainMs=0 방어 — JIT 최적화가 극단적이면 0 으로 측정되어 Infinity 출력 위험
        String overheadPct = (plainMs == 0)
            ? "N/A (plainMs=0, JIT 최적화 과함 — JMH 권장)"
            : String.format("%.1f %%", ((double)(advisedMs - plainMs) / plainMs * 100));
        MeasurementLog.row("오버헤드", (advisedMs - plainMs) + " ms (" + overheadPct + ")");
        MeasurementLog.row("회당 추가 비용", String.format("%.1f ns", (double)(advisedMs - plainMs) * 1_000_000 / iterations));

        MeasurementLog.section("학습 포인트");
        System.out.println("  · JIT 웜업 후 회당 오버헤드 = 보통 수 ~ 수십 ns");
        System.out.println("  · 메서드 자체가 무거우면 비율 무시 가능. 가벼우면 오버헤드 비율이 ↑");
        System.out.println("  · Around 안에 로직 추가하면 그만큼 오버헤드 ↑ (advice 본문 비용)");
        System.out.println("  · 본인 측정값을 measurements.md 에 기록 — 환경마다 다름");
        System.out.println("  · 정밀 측정은 JMH 권장. 여기는 경향 파악용 (DCE 방어 위해 sink 누적)");

        ctx.close();
    }
}
