package stage.s1;

import domain.NotificationService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 1-3: getBean() 명시 조회 (Service Locator) vs @Autowired 주입 (DI).
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>방법 A: ctx.getBean() — 본인 코드가 컨테이너에 의존. 안티패턴</li>
 *   <li>방법 B: @Autowired — 의존성이 생성자 시그니처에 명시. 컨테이너 의존 X</li>
 * </ul>
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage1GetBeanVsAutowired
 * </pre>
 */
public class Stage1GetBeanVsAutowired {

    @Configuration
    @ComponentScan(basePackages = "domain")
    static class Config {}

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);

        System.out.println("\n=== 방법 A: getBean() 명시 조회 (Service Locator) ===");
        NotificationService service = ctx.getBean(NotificationService.class);
        service.notify("user@example.com", "방법 A 로 조회한 서비스");

        System.out.println("\n=== 방법 B: @Autowired 생성자 주입 ===");
        System.out.println("  (위 NotificationService 의 생성자 로그 참고 — sender 가 이미 주입됨)");
        System.out.println("  본인이 ctx.getBean() 호출 없이도, 컨테이너가 NotificationSender 를 알아서 꽂아줌");

        ctx.close();

        System.out.println("\n[학습 포인트]");
        System.out.println("  방법 A: 본인 코드 어디서든 ctx 에 접근해야 함 → 컨테이너 강결합 → 테스트 어려움");
        System.out.println("  방법 B: 의존성이 생성자 파라미터에 명시 → 테스트 시 new Service(mockSender) 가능");
        System.out.println("  → 방법 B (DI) 가 표준. 방법 A 는 옛 코드 / 동적 조회 필요 시만");
    }
}
