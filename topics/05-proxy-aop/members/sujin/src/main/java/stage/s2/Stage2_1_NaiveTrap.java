package stage.s2;

import domain.RoleRepository;
import domain.RoleService;
import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = "domain")     // domain 패키지 스캔
public class Stage2_1_NaiveTrap {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
            SpringApplication.run(Stage2_1_NaiveTrap.class, args);

        RoleService    svc  = ctx.getBean(RoleService.class);
        RoleRepository repo = ctx.getBean(RoleRepository.class);

        try {
            svc.grantRole(7L, "ADMIN", true);
        } catch (Exception e) {
            System.out.println("예외 발생: " + e.getMessage());
        }

        long roleCount = repo.countRole(7L, "ADMIN");
        System.out.println("=> user_role 행 수 = " + roleCount + "  (기대: 0 — 롤백됐어야 함)");

        MeasurementLog.save("s2", "순진한 @MyTransactional 함정 — 예외 후 user_role 행 수="
            + roleCount + " (0이면 정상 / 1이면 함정 재현)");
        ctx.close();
    }
}
