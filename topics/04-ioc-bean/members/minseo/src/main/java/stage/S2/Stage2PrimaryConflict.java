package stage.S2;

import domain.AuthProvider;
import infra.MeasurementLog;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

/**
 * STAGE 2-5: @Primary 의 동작과 @Qualifier 와의 우선순위 충돌
 */
public class Stage2PrimaryConflict {

    // 1. @Qualifier 없이 인터페이스 타입만으로 주입받는 서비스
    @Service
    static class DefaultAuthService {
        private final AuthProvider provider;

        // @Qualifier가 없습니다! AuthProvider는 4개나 있는데 누굴 골라야 할까요?
        public DefaultAuthService(AuthProvider provider) {
            this.provider = provider;
        }

        public void use() {
            System.out.print("[DefaultAuthService] 누가 주입되었나요? -> ");
            provider.authenticate("test", "test", "test");
        }
    }

    @Configuration
    @org.springframework.context.annotation.Import(infra.DataSourceConfig.class)
    @ComponentScan(basePackages = "domain")
    static class Config {}

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);

        System.out.println("\n=== @Qualifier 가 없을 때의 동작 ===");
        // JwtAuthProvider.java 소스를 보면 @Primary 어노테이션이 붙어있습니다.
        // 따라서 @Qualifier로 콕 집어 말해주지 않으면 스프링은 무조건 @Primary가 붙은 녀석을 기본값으로 줍니다.
        DefaultAuthService service = ctx.getBean(DefaultAuthService.class);
        service.use();

        ctx.close();

        MeasurementLog.save("s2-5", "@Primary 기본값 동작", 
            "@Qualifier가 없을 때 @Primary가 붙은 JwtAuthProvider가 기본으로 주입됨을 확인");

        System.out.println("\n[학습 포인트]");
        System.out.println("  - 빈이 여러 개일 때 @Qualifier가 없으면 에러가 납니다 (NoUniqueBeanDefinitionException).");
        System.out.println("  - 하지만 그중 하나에 @Primary가 붙어있다면, 걔가 '기본값'이 되어 에러 없이 주입됩니다.");
        System.out.println("  - 만약 @Primary가 붙어있는 녀석이 있는데, @Qualifier로 다른 애를 지목한다면? -> @Qualifier(명시적 지정)가 이깁니다!");
    }
}