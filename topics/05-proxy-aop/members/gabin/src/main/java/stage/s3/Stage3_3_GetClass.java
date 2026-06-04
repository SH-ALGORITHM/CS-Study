package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
    Stage3_3_GetClass.NoInterfaceNoTx.class,
    Stage3_3_GetClass.NoInterfaceWithTx.class,
    Stage3_3_GetClass.HasInterfaceNoTx.class,
    Stage3_3_GetClass.HasInterfaceWithTx.class
})
public class Stage3_3_GetClass {

    @Service
    public static class NoInterfaceNoTx {
        public String work() {
            return "1";
        }
    }

    @Service
    public static class NoInterfaceWithTx {
        @Transactional
        public String work() {
            return "2";
        }
    }

    public interface SomeService {
        String work();
    }

    @Service
    public static class HasInterfaceNoTx implements SomeService {
        public String work() {
            return "3";
        }
    }

    public interface OtherService {
        String work();
    }

    @Service
    public static class HasInterfaceWithTx implements OtherService {
        @Transactional
        public String work() {
            return "4";
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_3_GetClass.class, args);

        String noInterfaceNoTx = ctx.getBean(NoInterfaceNoTx.class).getClass().getName();
        String noInterfaceWithTx = ctx.getBean(NoInterfaceWithTx.class).getClass().getName();
        String hasInterfaceNoTx = ctx.getBean(HasInterfaceNoTx.class).getClass().getName();
        String hasInterfaceWithTx = ctx.getBean(HasInterfaceWithTx.class).getClass().getName();

        MeasurementLog.title("STAGE 3-3 — getClass() 매트릭스");
        MeasurementLog.row("인터페이스 X + TX X", noInterfaceNoTx);
        MeasurementLog.row("인터페이스 X + TX O", noInterfaceWithTx);
        MeasurementLog.row("인터페이스 O + TX X", hasInterfaceNoTx);
        MeasurementLog.row("인터페이스 O + TX O", hasInterfaceWithTx);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · @Transactional 없으면 가로챌 advice가 없어서 프록시를 만들지 않는다.");
        System.out.println("  · Spring Boot 2.0+ 기본은 인터페이스 유무와 무관하게 CGLIB다.");

        MeasurementLog.save("s3-3", "getClass 매트릭스",
            "TX 있는 Bean 은 CGLIB 프록시 클래스명 출력");

        ctx.close();
    }
}
