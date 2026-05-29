package stage.s4;

import domain.Audited;
import domain.MyTransactional;
import domain.MyTransactionalAspect;
import infra.MeasurementLog;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * STAGE 4-2 — self-invocation 해결 3 가지.
 *
 * <h3>해결책 비교</h3>
 * <ul>
 *   <li>(a) 자기 자신 주입 (@Autowired @Lazy) — 동작은 함. 설계 어색</li>
 *   <li>(b) ApplicationContext — Service Locator 패턴. 안티</li>
 *   <li>(c) 클래스 분리 — 가장 권장. 근본 해결</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = "stage.s4")
@ComponentScan(
    basePackages = {"stage.s4", "domain"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {domain.AuditAspect.class, domain.OrderService.class, domain.OrderRepository.class}
    )
)
public class Stage4_2_Resolve {

    @Bean
    public MyTransactionalAspect myTransactionalAspect(DataSource ds) {
        return new MyTransactionalAspect(ds);
    }

    // 해결 (a) — 자기 자신 주입
    @Service
    public static class SelfInjectedService {
        // 생성자 주입은 자기 자신 → 생성자 순환 참조로 부팅 실패.
        // → 필드 주입 (+ @Lazy 프록시) 만 가능. 생성자 주입 권장 원칙의 예외 케이스.
        @Autowired @Lazy
        private SelfInjectedService self;

        @MyTransactional
        @Audited(action = "OUTER")
        public void outer() {
            System.out.println("  [Self-Inject] outer 시작");
            self.inner();   // 프록시 거침 → [TX] begin 출력됨
        }

        @MyTransactional
        @Audited(action = "INNER")
        public void inner() {
            System.out.println("  [Self-Inject] inner 호출됨");
        }
    }

    // 해결 (c) — 클래스 분리
    @Service
    public static class OuterService {
        private final InnerService inner;
        public OuterService(InnerService inner) { this.inner = inner; }

        @MyTransactional
        @Audited(action = "OUTER")
        public void outer() {
            System.out.println("  [Split] outer 시작");
            inner.inner();   // 다른 객체의 프록시 → [TX] begin 출력됨
        }
    }

    @Service
    public static class InnerService {
        @MyTransactional
        @Audited(action = "INNER")
        public void inner() {
            System.out.println("  [Split] inner 호출됨");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_2_Resolve.class, args);

        MeasurementLog.title("STAGE 4-2 — self-invocation 해결 3 가지");

        MeasurementLog.section("해결 (a) 자기 자신 주입 — 동작은 함 / 설계 어색");
        ctx.getBean(SelfInjectedService.class).outer();

        MeasurementLog.section("해결 (c) 클래스 분리 — 권장");
        ctx.getBean(OuterService.class).outer();

        MeasurementLog.section("학습 포인트");
        System.out.println("  · 두 해결책 모두 inner 의 [TX] begin 출력됨 — 함정 해결 확인");
        System.out.println("  · (a) 는 @Lazy 필수 (생성자 순환 참조 회피, 4 주차 STAGE 4-3 회수)");
        System.out.println("  · (c) 가 권장 — 두 메서드의 결합 분리 → 단위 테스트도 분리 가능");
        System.out.println("  · (b) 예: ctx.getBean(OrderService.class).inner()");
        System.out.println("        ApplicationContext 주입 = Service Locator 안티패턴 — 컨테이너 강결합 / 테스트 어려움");

        ctx.close();
    }
}
