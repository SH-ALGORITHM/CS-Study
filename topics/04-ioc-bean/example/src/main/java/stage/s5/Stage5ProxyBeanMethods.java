package stage.s5;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 5 (보너스): @Configuration(proxyBeanMethods) 동작 차이 — 5 주차 AOP 브릿지.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>true (기본): CGLIB 프록시가 @Bean 메서드 호출을 가로채서 캐싱된 싱글톤 반환</li>
 *   <li>false (Lite Mode): 순수 자바 동작. dep() 직접 호출 시 매번 new</li>
 * </ul>
 *
 * <h3>왜 이게 5 주차 AOP 브릿지인가?</h3>
 * 자바 코드로는 분명히 메서드 호출했는데, 스프링이 중간에 개입 (프록시) 해서 다른 동작 (캐싱된 빈 반환) 으로
 * 바꾸는 메커니즘. 5 주차의 @Transactional / @Aspect 가 동일 원리 — 메서드 호출을 프록시가 가로채서
 * 트랜잭션 begin/commit 을 끼워넣음.
 *
 * <h3>실행</h3>
 * <pre>
 * ./gradlew run -PmainClass=stage.s5.Stage5ProxyBeanMethods
 * </pre>
 */
public class Stage5ProxyBeanMethods {

    static class SomeDependency {
        public SomeDependency() {
            System.out.println("    [SomeDependency] 생성자 호출");
        }
    }

    /** proxyBeanMethods = true (기본값) — CGLIB 프록시 적용 */
    @Configuration(proxyBeanMethods = true)
    static class ConfigTrue {
        @Bean
        public SomeDependency dep() {
            return new SomeDependency();
        }

        @Bean
        public String beanA() {
            dep();   // 직접 호출 1 — 프록시가 가로채서 캐싱된 싱글톤 반환
            return "A";
        }

        @Bean
        public String beanB() {
            dep();   // 직접 호출 2 — 동일
            return "B";
        }
    }

    /** proxyBeanMethods = false (Lite Mode) — 프록시 없음 */
    @Configuration(proxyBeanMethods = false)
    static class ConfigFalse {
        @Bean
        public SomeDependency dep() {
            return new SomeDependency();
        }

        @Bean
        public String beanA() {
            dep();   // 직접 호출 1 — 순수 자바, new
            return "A";
        }

        @Bean
        public String beanB() {
            dep();   // 직접 호출 2 — 또 new
            return "B";
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. proxyBeanMethods = true (기본값) ===");
        var ctx1 = new AnnotationConfigApplicationContext(ConfigTrue.class);
        System.out.println("  → 생성자 1 회만 호출. CGLIB 프록시가 @Bean 메서드 호출 가로채서 싱글톤 보장");
        ctx1.close();

        System.out.println("\n=== 2. proxyBeanMethods = false (Lite Mode) ===");
        var ctx2 = new AnnotationConfigApplicationContext(ConfigFalse.class);
        System.out.println("  → 생성자 3 회 호출 (Bean 등록 1 + beanA 안 dep() 1 + beanB 안 dep() 1)");
        System.out.println("     프록시 없으므로 순수 자바처럼 매번 new — 싱글톤 보장 X");
        ctx2.close();

        System.out.println("\n[학습 포인트]");
        System.out.println("  true (기본):  싱글톤 보장 / 부팅 시 CGLIB 프록시 생성 비용");
        System.out.println("  false:        싱글톤 보장 X / 부팅 빠름 / @Bean 메서드 직접 호출 시 위험");
        System.out.println("  → 5 주차 AOP: @Transactional / @Aspect 가 동일하게 \"프록시로 메서드 호출 가로채기\" 원리");
    }
}
