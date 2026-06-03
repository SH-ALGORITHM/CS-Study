package stage.s3;

import domain.Audited;
import infra.MeasurementLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * STAGE 3-1 — AOP 적용 전후 응답 시간 (오버헤드 측정).
 * 감사 로그 도메인 적용.
 */
@SpringBootApplication(scanBasePackages = "stage.s3")
@ComponentScan(
    basePackages = {"stage.s3", "domain", "infra"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            domain.AuditAspect.class, // 기존 무거운 로그 출력 Aspect 제외
            domain.MyTransactionalAspect.class,
            domain.NaiveTransactionalAspect.class,
            stage.s2.Stage2_1_NaiveTrap.class,
            stage.s2.Stage2_1_ThreadLocal.class,
            stage.s2.Stage2_2_OrderChaining.class,
            stage.s2.Stage2_3_Pointcut.class,
            stage.s2.Stage2_4_FiveAdvice.class,
            stage.s2.Stage2_5_Audited.class
        }
    )
)
public class Stage3_1_Overhead {

    @Service
    public static class PlainService {
        public int doWork() { return 42; }
    }

    @Service
    public static class AuditedService {
        @Audited(action = "MEASURE")
        public int doWork() { return 42; }
    }

    @Aspect
    @Component
    public static class LightAuditAspect {
        // 성능 측정을 위해 System.out을 제거한 가벼운 Aspect 사용
        @Around("@annotation(audited)")
        public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
            return pjp.proceed(); 
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_1_Overhead.class, args);
        PlainService plain = ctx.getBean(PlainService.class);
        AuditedService audited = ctx.getBean(AuditedService.class);

        int warmup = 5_000;
        int iterations = 1_000_000;

        // 1. JIT 웜업
        long sink = 0;
        for (int i = 0; i < warmup; i++) {
            sink += plain.doWork();
            sink += audited.doWork();
        }

        // 2. 순수 서비스 측정
        long t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            sink += plain.doWork();
        }
        long plainMs = (System.nanoTime() - t1) / 1_000_000;

        // 3. AOP 적용 서비스 측정
        long t2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            sink += audited.doWork();
        }
        long advisedMs = (System.nanoTime() - t2) / 1_000_000;

        // DCE 방지
        System.out.println("  (sink check = " + sink + ")");

        MeasurementLog.title("STAGE 3-1 — AOP 오버헤드 측정 (100만 회)");
        MeasurementLog.row("일반 메서드 호출 (1M 회)", plainMs + " ms");
        MeasurementLog.row("@Audited 메서드 호출 (1M 회)", advisedMs + " ms");
        
        long diff = advisedMs - plainMs;
        String overheadPct = (plainMs == 0) ? "N/A" : String.format("%.1f%%", (double) diff / plainMs * 100);
        MeasurementLog.row("순수 AOP 오버헤드", diff + " ms (" + overheadPct + ")");
        MeasurementLog.row("회당 추가 지연 시간", String.format("%.2f ns", (double) diff * 1_000_000 / iterations));

        ctx.close();
    }
}
