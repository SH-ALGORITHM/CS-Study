package stage.s2;

import domain.AdminTaskService;
import domain.SecurityContext;
import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = "domain")
public class Stage2_5_RequireRole {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
            SpringApplication.run(Stage2_5_RequireRole.class, args);

        AdminTaskService svc = ctx.getBean(AdminTaskService.class);

        // (1) ADMIN → 통과
        SecurityContext.setRole("ADMIN");
        String r1;
        try { r1 = svc.deleteUser(42L); }
        catch (Exception e) { r1 = "거부(" + e.getClass().getSimpleName() + "): " + e.getMessage(); }
        System.out.println("[ADMIN] " + r1);
        SecurityContext.clear();

        // (2) USER → 거부 (메서드 실행 자체가 안 됨)
        SecurityContext.setRole("USER");
        String r2;
        try { r2 = svc.deleteUser(42L); }
        catch (Exception e) { r2 = "거부(" + e.getClass().getSimpleName() + "): " + e.getMessage(); }
        System.out.println("[USER]  " + r2);
        SecurityContext.clear();

        MeasurementLog.save("s2", "@RequireRole(@Before) — ADMIN→" + r1 + " / USER→" + r2);
        ctx.close();
    }
}
