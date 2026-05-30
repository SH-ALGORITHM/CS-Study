package stage.S1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class Stage1BootCount {
    public static void main(String[] args) {
        System.out.println("=== [STAGE 1-4] Spring Boot 실행 ===");
        // SpringApplication.run()은 스프링 부트를 기동하고 컨테이너(ctx)를 반환합니다.
        ApplicationContext ctx = SpringApplication.run(Stage1BootCount.class, args);
        System.out.println("\n=======================================");
        System.out.println("스프링 부트가 만든 전체 빈 개수: " + ctx.getBeanDefinitionCount());
        System.out.println("=======================================\n");
        // 너무 많으니 상위 10개만 출력해볼까요?
        String[] beanNames = ctx.getBeanDefinitionNames();
        for (int i = 0; i < Math.min(10, beanNames.length); i++) {
            System.out.println((i + 1) + ". " + beanNames[i]);
        }
        System.out.println("... 등등 수많은 빈이 등록되었습니다.");
    }
}
