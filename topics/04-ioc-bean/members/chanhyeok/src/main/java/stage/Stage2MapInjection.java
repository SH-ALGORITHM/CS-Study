package stage;

import domain.PaymentDispatcher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * STAGE 2-4 보너스 — Map<String, PaymentGateway> 자동 주입 시연.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>PaymentDispatcher 가 PG 3 개를 Map 으로 자동 주입받음</li>
 *   <li>키 = "toss" / "kakao" / "naver" (@Component value)</li>
 *   <li>잘못된 키 ("payco" 미등록) → 안전 처리</li>
 * </ul>
 */
public class Stage2MapInjection {

    @Configuration
    @ComponentScan(basePackages = "domain")
    static class Config {}

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);
        PaymentDispatcher dispatcher = ctx.getBean(PaymentDispatcher.class);

        System.out.println("\n=== 사용자가 선택한 PG 로 분기 ===");
        dispatcher.pay("toss", 1L, new BigDecimal("10000"));
        dispatcher.pay("kakao", 2L, new BigDecimal("5000"));
        dispatcher.pay("naver", 3L, new BigDecimal("3000"));

        System.out.println("\n=== 잘못된 키 (등록 안 된 PG) ===");
        dispatcher.pay("payco", 4L, new BigDecimal("1000"));

        ctx.close();

        System.out.println("\n[학습 포인트]");
        System.out.println("  Map<String, T> 자동 주입 → 키는 Bean 이름");
        System.out.println("  새 PG 추가 = @Component 클래스 1 개 (PaymentDispatcher 코드 수정 X) → OCP");
    }
}
