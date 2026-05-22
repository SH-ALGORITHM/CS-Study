package stage.s1;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * STAGE 1-4: @SpringBootApplication 자동 등록 Bean 수 확인.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>본인이 만든 Bean 은 4 (Email/Sms/Push/Slack) + 1 (NotificationService) = 5 개</li>
 *   <li>그런데 ctx.getBeanDefinitionCount() 는 100~200 개 → @EnableAutoConfiguration 의 마법</li>
 *   <li>이름 다 출력하면 콘솔 폭발 — Spring Boot 가 뒤에서 얼마나 많은 짓을 하는지 체감</li>
 * </ul>
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.Stage1BootCount
 * </pre>
 */
@SpringBootApplication(scanBasePackages = "domain")
public class Stage1BootCount {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1BootCount.class, args);

        int count = ctx.getBeanDefinitionCount();

        System.out.println("\n=== @SpringBootApplication 자동 등록 Bean 수 ===");
        System.out.println("총 Bean 수: " + count + "개");

        System.out.println("\n=== Bean 이름 전체 (콘솔 폭발 주의) ===");
        for (String name : ctx.getBeanDefinitionNames()) {
            System.out.println("  - " + name);
        }

        System.out.println("\n[학습 포인트]");
        System.out.println("  - 본인이 만든 Bean: NotificationService + EmailSender/SmsSender/PushSender/SlackSender = 5 개");
        System.out.println("  - 나머지 " + (count - 5) + "개는 @EnableAutoConfiguration 이 추가한 것");
        System.out.println("    (DataSource / Jackson / WebMvc 등 — 의존성에 따라 다름)");
        System.out.println("  - 부팅 시간 늘어나는 주범 → @Lazy / 의존성 제거 / Native Image 등으로 최적화");

        MeasurementLog.save("s1-4", "@SpringBootApplication", "Bean " + count + "개 자동 등록");

        ctx.close();
    }
}
