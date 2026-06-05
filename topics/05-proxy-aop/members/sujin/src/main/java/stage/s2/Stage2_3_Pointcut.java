package stage.s2;

import domain.AdminTaskService;
import domain.SecurityContext;
import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = "domain")
public class Stage2_3_Pointcut {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
            SpringApplication.run(Stage2_3_Pointcut.class, args);

        AdminTaskService svc = ctx.getBean(AdminTaskService.class);
        SecurityContext.setRole("ADMIN");

        System.out.println("=== deleteUser (@RequireRole) — exec/anno/within 3개 다 매칭 ===");
        svc.deleteUser(42L);

        System.out.println("\n=== viewUser (어노테이션 없음) — exec/within 2개만 ===");
        svc.viewUser(42L);

        SecurityContext.clear();
        MeasurementLog.save("s2", "Pointcut 3종 — deleteUser=exec+anno+within(3) / viewUser=exec+within(2, @annotation 제외)");
        ctx.close();
    }
}
