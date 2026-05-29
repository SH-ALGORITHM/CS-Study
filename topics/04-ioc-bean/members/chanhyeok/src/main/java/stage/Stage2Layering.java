package stage;

import domain.PaymentService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * STAGE 2-2 / 2-3 학습용 main — 결제 PG 도메인 계층 분리 + 생성자 주입 확인.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>PaymentService 의 생성자에 @Qualifier("toss") 명시 → TossPayment 주입</li>
 *   <li>4 개 sender 모두 라이프사이클 (@PostConstruct) 호출 확인</li>
 *   <li>ctx.close() 시 @PreDestroy 역순 호출</li>
 * </ul>
 */
public class Stage2Layering {

    @Configuration
    @ComponentScan(basePackages = "domain")
    static class Config {}

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);

        PaymentService service = ctx.getBean(PaymentService.class);
        service.pay(1L, new BigDecimal("10000"));

        ctx.close();
    }
}
