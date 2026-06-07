package stage.s1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import infra.MeasurementLog;

/**
 * STAGE 1-4 — Spring 의 프록시 선택 규칙 (JDK vs CGLIB).
 */
@SpringBootApplication(scanBasePackages = "stage.s1")
public class Stage1RuleMain {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1RuleMain.class, args);

        // 1. 인터페이스가 있는 서비스
        TransferService interfaceService = ctx.getBean(TransferService.class);
        // 2. 인터페이스가 없는 서비스
        NoInterfaceService noInterfaceService = ctx.getBean(NoInterfaceService.class);

        MeasurementLog.title("Spring 프록시 선택 규칙 확인");
        MeasurementLog.row("인터페이스 있는 서비스", MeasurementLog.classOf(interfaceService));
        MeasurementLog.row("인터페이스 없는 서비스", MeasurementLog.classOf(noInterfaceService));

        MeasurementLog.section("학습 포인트");
        System.out.println("  · 인터페이스가 있어도 Spring Boot 기본 설정은 CGLIB 사용 ($$SpringCGLIB)");
        System.out.println("  · 인터페이스가 없으면 무조건 CGLIB 사용");
        System.out.println("  · application.properties 에 spring.aop.proxy-target-class=false 설정 시 JDK 사용 ($Proxy)");

        ctx.close();
    }

    public interface TransferService {
        void transfer();
    }

    @Service
    public static class TransferServiceImpl implements TransferService {
        @Transactional
        @Override
        public void transfer() { System.out.println("transfer"); }
    }

    @Service
    public static class NoInterfaceService {
        @Transactional
        public void work() { System.out.println("work"); }
    }
}
