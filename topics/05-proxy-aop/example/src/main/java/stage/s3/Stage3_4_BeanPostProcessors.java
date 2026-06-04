package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * STAGE 3-4 — BeanPostProcessor 변화 (4 주차 회상 + 5 주차 추가).
 *
 * <h3>4 주차에서 본 internal* 5 개 (순수 컨테이너 기준)</h3>
 * <ul>
 *   <li>internalConfigurationAnnotationProcessor</li>
 *   <li>internalAutowiredAnnotationProcessor</li>
 *   <li>internalCommonAnnotationProcessor</li>
 *   <li>internalEventListenerProcessor</li>
 *   <li>internalEventListenerFactory</li>
 * </ul>
 *
 * <h3>5 주차에 추가</h3>
 * <ul>
 *   <li>internalAutoProxyCreator (= AnnotationAwareAspectJAutoProxyCreator)</li>
 *   <li>→ 모든 @Aspect 를 찾아서 매칭되는 Bean 을 프록시로 교체하는 주체</li>
 * </ul>
 *
 * <p>⚠️ Spring Boot 환경에서는 internalCachingMetadataReaderFactory 등 추가 internal* 빈이
 * 자동 설정으로 더 등장한다. 핵심은 "internalAutoProxyCreator 1 개 새로 등장" — 총 개수가 6 으로
 * 딱 맞아떨어지지는 않음 (환경마다 다름). 4 주차 example 의 순수 AnnotationConfigApplicationContext
 * 와 비교하면 정확히 +1 차이.
 */
@Configuration
@EnableAutoConfiguration
@org.springframework.context.annotation.ComponentScan(
    basePackages = "stage.s3",
    excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = {Stage3_1_Overhead.class, Stage3_3_GetClass.class}
    )
)
public class Stage3_4_BeanPostProcessors {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_4_BeanPostProcessors.class, args);

        MeasurementLog.title("STAGE 3-4 — BeanPostProcessor (internal* / AutoProxy)");

        MeasurementLog.section("internal* + AutoProxy Bean 목록");
        int total = 0;
        for (String name : ctx.getBeanDefinitionNames()) {
            if (name.contains("internal") || name.toLowerCase().contains("autoproxy")) {
                System.out.println("  - " + name);
                total++;
            }
        }
        MeasurementLog.row("internal* + AutoProxy 개수", total);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · 핵심: internalAutoProxyCreator (= AnnotationAwareAspectJAutoProxyCreator) 가 새로 등장");
        System.out.println("  · 총 개수는 Boot 자동설정 때문에 환경마다 다름 — 6 개로 딱 안 떨어질 수 있음");
        System.out.println("  · postProcessAfterInitialization 시점에 Bean → 프록시 교체");
        System.out.println("  · @Aspect 클래스 찾아서 Pointcut 매칭되는 모든 Bean 가공");

        ctx.close();
    }
}
