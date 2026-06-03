package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * STAGE 3-4 — BeanPostProcessor의 변화 확인.
 * AOP가 활성화되었을 때 어떤 내부 빈이 추가되는지 관찰합니다.
 */
@SpringBootApplication(scanBasePackages = "stage.s3")
@ComponentScan(
    basePackages = {"stage.s3", "domain", "infra"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            Stage3_1_Overhead.class,
            Stage3_2_JdkVsCglib.class,
            Stage3_3_GetClass.class
        }
    )
)
public class Stage3_4_BeanPostProcessors {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_4_BeanPostProcessors.class, args);

        MeasurementLog.title("STAGE 3-4 — AOP 내부 엔진(AutoProxy) 확인");

        MeasurementLog.section("스프링 내부(internal*) 및 AutoProxy 빈 목록");
        int count = 0;
        for (String name : ctx.getBeanDefinitionNames()) {
            // internal로 시작하거나 autoproxy를 포함하는 빈 찾기
            if (name.contains("internal") || name.toLowerCase().contains("autoproxy")) {
                System.out.println("  - " + name);
                count++;
            }
        }

        MeasurementLog.row("검색된 내부 엔진 빈 개수", count);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · 4주차의 5개 핵심 빈 외에 'org.springframework.aop.config.internalAutoProxyCreator'가 등장함");
        System.out.println("  · 이 빈이 바로 @Aspect를 읽어서 빈을 프록시로 바꿔치기하는 '빈 후처리기'임");
        System.out.println("  · 4주차에 배운 BeanPostProcessor 메커니즘이 AOP의 근간임을 이해하는 것이 핵심");

        ctx.close();
    }
}
