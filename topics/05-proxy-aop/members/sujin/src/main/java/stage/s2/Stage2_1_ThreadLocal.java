package stage.s2;

import domain.RoleRepository;
import domain.RoleService;
import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = "domain")
public class Stage2_1_ThreadLocal {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
            SpringApplication.run(Stage2_1_ThreadLocal.class, args);

        RoleService    svc  = ctx.getBean(RoleService.class);
        RoleRepository repo = ctx.getBean(RoleRepository.class);

        // (1) 감사 로그 실패 → 권한 부여도 롤백되어야
        try { svc.grantRole(7L, "ADMIN", true); }
        catch (Exception e) { System.out.println("예외: " + e.getMessage()); }
        long failCount = repo.countRole(7L, "ADMIN");
        System.out.println("[실패] user_role(7,ADMIN) = " + failCount + "  (기대 0 — 롤백)");

        // (2) 정상 → 커밋
        svc.grantRole(8L, "USER", false);
        long okCount = repo.countRole(8L, "USER");
        System.out.println("[정상] user_role(8,USER)  = " + okCount + "  (기대 1 — 커밋)");

        MeasurementLog.save("s2", "ThreadLocal @MyTransactional — 실패시 user_role=" + failCount
            + " (0=롤백 성공) / 정상시 user_role=" + okCount + " (1=커밋)");
        ctx.close();
    }
}
