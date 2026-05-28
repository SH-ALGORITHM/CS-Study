package stage.S2;

import domain.AuthProvider;
import infra.MeasurementLog;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * STAGE 2-4: 다중 구현체 (Strategy 패턴) + Map 주입
 *
 * AuthProvider 인터페이스를 구현한 여러 빈(Jwt, Kakao, Google, Session)을
 * Map<String, AuthProvider> 형태로 한 번에 주입받아 사용하는 패턴 확인.
 */
public class Stage2MapInjection {

    // 클라이언트의 인증 요청을 받아 적절한 Provider로 토스하는 역할
    static class AuthDispatcher {
        
        // 핵심: 스프링이 AuthProvider 타입의 모든 빈을 긁어모아서 Map으로 넣어줌
        private final Map<String, AuthProvider> providers;

        public AuthDispatcher(Map<String, AuthProvider> providers) {
            this.providers = providers;
            System.out.println("[AuthDispatcher] 주입받은 provider 키 목록: " + providers.keySet());
        }

        public void authenticate(String strategyName, String id, String password, String token) {
            AuthProvider provider = providers.get(strategyName);
            if (provider == null) {
                System.out.println("⚠️ '" + strategyName + "' 에 해당하는 인증 방식이 없습니다.");
                return;
            }
            provider.authenticate(id, password, token);
        }
    }

    @Configuration
    @org.springframework.context.annotation.Import(infra.DataSourceConfig.class)
    @ComponentScan(basePackages = "domain")
    static class Config {
        @org.springframework.context.annotation.Bean
        public AuthDispatcher authDispatcher(Map<String, AuthProvider> providers) {
            return new AuthDispatcher(providers);
        }
    }

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);
        AuthDispatcher dispatcher = ctx.getBean(AuthDispatcher.class);

        System.out.println("\n=== 전략(Strategy)을 Map 키로 골라서 인증 ===");
        
        // JwtAuthProvider에 @Component("jwt") 라고 지정해 두었다면 키는 "jwt"
        dispatcher.authenticate("jwt", "user1", "pass1", "jwt-token-123");
        
        // KakaoAuthProvider에는 이름 지정을 안 했다면 클래스명을 카멜케이스로 ("kakaoAuthProvider")
        dispatcher.authenticate("kakaoAuthProvider", "user2", "pass2", "kakao-token-abc");

        // 구글도 이름 지정을 안했다면 기본값 ("googleAuthProvider")
        dispatcher.authenticate("googleAuthProvider", "user3", "pass3", "google-token-xyz");

        System.out.println("\n=== 없는 전략을 골랐을 때 ===");
        dispatcher.authenticate("naver", "user4", "pass4", "token");

        ctx.close();

        // measurements.md 파일 자동 업데이트
        MeasurementLog.save("s2-4", "다형성 Map 자동 주입", 
            "AuthProvider 인터페이스를 구현한 모든 빈을 Map<String, AuthProvider>로 주입받아 OCP(개방폐쇄원칙) 만족 확인");
            
        System.out.println("\n[학습 포인트]");
        System.out.println("  - Map<String, 인터페이스> 로 받으면 스프링이 알아서 구현체들을 모아줍니다.");
        System.out.println("  - Map의 Key는 '빈의 이름'이 됩니다. (ex: @Component(\"jwt\") 라면 키는 \"jwt\")");
        System.out.println("  - 새로운 인증 방식(예: NaverAuthProvider)이 추가되어도 AuthDispatcher 클래스는 단 한 줄도 수정할 필요가 없습니다. (OCP 달성!)");
    }
}