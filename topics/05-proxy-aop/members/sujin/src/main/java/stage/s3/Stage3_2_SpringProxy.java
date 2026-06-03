package stage.s3;

import domain.AdminTaskService;
import domain.RoleService;
import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = "domain")
public class Stage3_2_SpringProxy {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
            SpringApplication.run(Stage3_2_SpringProxy.class, args);

        // getClass 매트릭스
        System.out.println("AdminTaskService(@RequireRole)          = "
            + ctx.getBean(AdminTaskService.class).getClass().getName());
        System.out.println("RoleService(@MyTransactional+RequireRole) = "
            + ctx.getBean(RoleService.class).getClass().getName());

        // BeanPostProcessor — AutoProxyCreator (4주차 internal* + 5주차 추가분)
        System.out.println("\n[internal / AutoProxy 관련 빈]");
        int count = 0;
        for (String name : ctx.getBeanDefinitionNames()) {
            if (name.contains("internal") || name.toLowerCase().contains("autoproxy")) {
                System.out.println("  - " + name);
                count++;
            }
        }
        MeasurementLog.save("s3", "internal/AutoProxy 빈 " + count
            + "개 — AnnotationAwareAspectJAutoProxyCreator 가 @Aspect 프록시 생성 주체");
        ctx.close();
    }
}
