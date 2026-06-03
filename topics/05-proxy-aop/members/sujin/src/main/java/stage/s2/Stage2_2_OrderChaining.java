package stage.s2;

import domain.RoleRepository;
import domain.RoleService;
import domain.SecurityContext;
import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = "domain")
public class Stage2_2_OrderChaining {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
            SpringApplication.run(Stage2_2_OrderChaining.class, args);

        RoleService    svc  = ctx.getBean(RoleService.class);
        RoleRepository repo = ctx.getBean(RoleRepository.class);

        System.out.println("=== (1) ADMIN 호출 — 정상 ===");
        SecurityContext.setRole("ADMIN");
        try { svc.grantRole(9L, "USER", false); }
        catch (Exception e) { System.out.println("예외: " + e.getMessage()); }
        SecurityContext.clear();

        System.out.println("\n=== (2) USER 호출 — 권한 거부 (트랜잭션 안 열려야) ===");
        SecurityContext.setRole("USER");
        try { svc.grantRole(10L, "USER", false); }
        catch (Exception e) { System.out.println("예외: " + e.getMessage()); }
        long denied = repo.countRole(10L, "USER");
        System.out.println("user_role(10,USER) = " + denied + "  (기대 0 — 트랜잭션 시작도 안 됨)");
        SecurityContext.clear();

        MeasurementLog.save("s2", "@Order 양파 — 권한 거부 시 [TX] begin 미출력 / user_role(10)="
            + denied + " (AuthAspect @Order(1) 이 TX 바깥)");
        ctx.close();
    }
}
