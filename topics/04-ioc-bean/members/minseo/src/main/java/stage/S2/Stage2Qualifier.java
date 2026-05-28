package stage.S2;

import domain.AuthProvider;
import domain.AuthService;
import infra.MeasurementLog;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 2-4: @Qualifier 로 여러 빈 중 하나를 명시적으로 지정하기
 */
public class Stage2Qualifier {

    @Configuration
    @org.springframework.context.annotation.Import(infra.DataSourceConfig.class)
    @ComponentScan(basePackages = "domain")
    static class Config {}

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);

        System.out.println("\n=== [1] AuthService 내부의 @Qualifier(\"jwt\") 동작 확인 ===");
        AuthService service = ctx.getBean(AuthService.class);
        service.login("user1", "pass", "token-1");

        System.out.println("\n=== [2] ctx.getBean(이름, 타입) 으로 4개의 전략 직접 꺼내기 ===");
        // 빈의 이름을 정확히 알아야 꺼낼 수 있습니다.
        AuthProvider jwt = ctx.getBean("jwt", AuthProvider.class);
        AuthProvider kakao = ctx.getBean("kakao", AuthProvider.class);
        AuthProvider google = ctx.getBean("google", AuthProvider.class);

        // SessionAuthProvider는 이름을 명시하지 않았으므로 클래스명 카멜케이스가 이름이 됩니다.
        AuthProvider session = ctx.getBean("session", AuthProvider.class);

        jwt.authenticate("id", "pw", "jwt-token");
        kakao.authenticate("id", "pw", "kakao-token");
        google.authenticate("id", "pw", "google-token");
        session.authenticate("id", "pw", "session-token");

        ctx.close();

        MeasurementLog.save("s2-4", "@Qualifier 직접 조회",
            "빈 이름을 명시하여 ctx.getBean(\"이름\", 타입)으로 4개의 다형성 객체를 독립적으로 조회 확인");

        System.out.println("\n[학습 포인트]");
        System.out.println("  - @Component(\"이름\") 으로 지정한 값이 곧 빈의 이름이자 Qualifier 매칭 키가 됩니다.");
        System.out.println("  - @Qualifier가 있으면 무조건 이름 매칭이 최우선입니다.");
    }
}
