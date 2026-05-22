package stage.s2;

import domain.NotificationSender;
import domain.NotificationService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 2-4: @Qualifier 로 같은 타입 Bean 4 개 중 1 개 명시 지정.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>domain.NotificationService 가 @Qualifier("email") 로 EmailSender 지정</li>
 *   <li>ctx.getBean("email", NotificationSender.class) 로 이름 기반 직접 조회</li>
 *   <li>@Component("email") 의 value 가 Bean 이름이 된다는 점 확인</li>
 * </ul>
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage2Qualifier
 * </pre>
 */
public class Stage2Qualifier {

    @Configuration
    @ComponentScan(basePackages = "domain")
    static class Config {}

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Config.class);

        System.out.println("\n=== NotificationService 가 @Qualifier(\"email\") 로 받은 sender 사용 ===");
        NotificationService service = ctx.getBean(NotificationService.class);
        service.notify("user@example.com", "@Qualifier 로 Email 지정");

        System.out.println("\n=== ctx.getBean(이름, 타입) 으로 4 개 sender 직접 조회 ===");
        NotificationSender email = ctx.getBean("email", NotificationSender.class);
        NotificationSender sms = ctx.getBean("sms", NotificationSender.class);
        NotificationSender push = ctx.getBean("push", NotificationSender.class);
        NotificationSender slack = ctx.getBean("slack", NotificationSender.class);

        email.send("user@example.com", "via email");
        sms.send("010-1234-5678", "via sms");
        push.send("device-id-1", "via push");
        slack.send("#general", "via slack");

        ctx.close();

        System.out.println("\n[학습 포인트]");
        System.out.println("  - @Component(\"email\") 의 value 가 Bean 이름 (= @Qualifier 매칭 키)");
        System.out.println("  - @Qualifier 없으면 → EmailSender 의 @Primary 가 작동 → Email 자동 선택");
        System.out.println("  - @Primary 도 없으면 → NoUniqueBeanDefinitionException (다음 케이스에서)");
    }
}
