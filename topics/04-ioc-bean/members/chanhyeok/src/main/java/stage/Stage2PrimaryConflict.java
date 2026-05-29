package stage;

import domain.PaymentGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 2-5 — @Primary 와 @Qualifier 우선순위 충돌.
 *
 * <h3>충돌 시나리오</h3>
 * <ul>
 *   <li>TossPayment 에 @Primary 붙어있음 (기본 PG)</li>
 *   <li>KakaoOnlyService 는 @Qualifier("kakao") 명시</li>
 *   <li>→ @Qualifier 가 이김 (KakaoPayment 주입됨)</li>
 *   <li>AutoService 는 @Qualifier 없음 → @Primary 작동 (TossPayment 주입)</li>
 * </ul>
 */
public class Stage2PrimaryConflict {

    /** @Qualifier("kakao") 로 KakaoPayment 명시 받는 Service */
    static class KakaoOnlyService {
        private final PaymentGateway gateway;
        public KakaoOnlyService(PaymentGateway gateway) {
            this.gateway = gateway;
        }
        public PaymentGateway getGateway() { return gateway; }
    }

    /** @Qualifier 없이 자동 주입받는 Service (→ @Primary 가 작동) */
    static class AutoService {
        private final PaymentGateway gateway;
        public AutoService(PaymentGateway gateway) {
            this.gateway = gateway;
        }
        public PaymentGateway getGateway() { return gateway; }
    }

    @Configuration
    @ComponentScan(basePackages = "domain")
    static class Config {
        @Bean
        public KakaoOnlyService kakaoOnlyService(@Qualifier("kakao") PaymentGateway gateway) {
            return new KakaoOnlyService(gateway);
        }

        @Bean
        public AutoService autoService(PaymentGateway gateway) {
            return new AutoService(gateway);
        }
    }

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);

        System.out.println("\n=== 충돌 시나리오 ===");
        System.out.println("  TossPayment 에 @Primary 붙어있음");
        System.out.println("  KakaoOnlyService 는 @Qualifier(\"kakao\") 명시");
        System.out.println("  AutoService 는 @Qualifier 없음 (자동)\n");

        KakaoOnlyService kakaoOnly = ctx.getBean(KakaoOnlyService.class);
        System.out.println("KakaoOnlyService 가 받은 PG: "
            + kakaoOnly.getGateway().getClass().getSimpleName()
            + "   ← @Qualifier 가 이김");

        AutoService auto = ctx.getBean(AutoService.class);
        System.out.println("AutoService 가 받은 PG:      "
            + auto.getGateway().getClass().getSimpleName()
            + "   ← @Primary 가 작동");

        ctx.close();

        System.out.println("\n[학습 포인트]");
        System.out.println("  @Primary : 같은 타입 여러 개 중 \"기본값\". @Qualifier 없을 때만 작동");
        System.out.println("  @Qualifier: 명시 지정. 항상 우선");
        System.out.println("  → @Primary 는 폴백, @Qualifier 는 핀포인트");
    }
}
