package stage.s2;

import domain.TracedService;
import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = "domain")
public class Stage2_4_FiveAdvice {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
            SpringApplication.run(Stage2_4_FiveAdvice.class, args);

        TracedService svc = ctx.getBean(TracedService.class);

        System.out.println("=== 정상 종료 ===");
        svc.ok();

        System.out.println("\n=== 예외 발생 ===");
        try { svc.fail(); }
        catch (Exception e) { System.out.println("(호출부) 예외 받음: " + e.getMessage()); }

        MeasurementLog.save("s2", "Advice 5종 순서 — 정상: [Around시작]>[Before]>메서드>[AfterReturning]>[After]>[Around종료]"
            + " (Around 종료가 After 뒤 = Spring 5.2.7+ 확인)");
        ctx.close();
    }
}
