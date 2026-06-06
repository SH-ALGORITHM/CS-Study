package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * STAGE 3-1 — @PreAuthorize 권한 검사.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>SecurityContext 에 USER 권한 설정 → adminOnly() 호출 → AccessDeniedException</li>
 *   <li>ADMIN 권한 설정 → adminOnly() 통과</li>
 *   <li>SpEL — 메서드 인자 / authentication.name 참조</li>
 *   <li>5 주차 @Aspect 와 같은 프록시 메커니즘</li>
 *   <li>거부 시 예외 = AccessDeniedException 또는 Spring 6.1+ 에서 그 하위 AuthorizationDeniedException</li>
 * </ul>
 *
 * <h3>SecurityAutoConfiguration 유지</h3>
 * exclude 하지 않고 최소 SecurityFilterChain (permitAll) 만 명시 →
 * 메서드 시큐리티 인프라 (MethodSecurityExpressionHandler 등) 정상 등록.
 */
@SpringBootApplication
@EnableMethodSecurity
public class Stage3_1_PreAuthorize {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 이 데모는 main 에서 빈 메서드 직접 호출 — 필터 안 거침. 부팅용 최소 체인.
        return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .csrf(c -> c.disable())
            .build();
    }

    @Bean
    public PostService postService() {
        return new PostService();
    }


    public static class PostService {
        @PreAuthorize("hasRole('USER')")
        public String listAll() {
            return "post list";
        }

        @PreAuthorize("hasRole('ADMIN')")
        public String adminOnly() {
            return "admin data";
        }

        @PreAuthorize("#email == authentication.name")
        public String mine(String email) {
            return "my data for " + email;
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_1_PreAuthorize.class, args);
        PostService svc = ctx.getBean(PostService.class);

        MeasurementLog.title("STAGE 3-1 — @PreAuthorize 권한 검사");

        MeasurementLog.section("(1) ROLE_USER 로 인증 — listAll OK / adminOnly 거부");
        setAuth("alice@example.com", "ROLE_USER");
        System.out.println("  listAll() = " + svc.listAll());
        try {
            svc.adminOnly();
            System.out.println("  💥 adminOnly 통과");
        } catch (AccessDeniedException e) {
            System.out.println("  ✓ adminOnly 거부: " + e.getMessage());
        }

        MeasurementLog.section("(2) ROLE_ADMIN 으로 인증 — adminOnly OK");
        setAuth("admin@example.com", "ROLE_ADMIN");
        System.out.println("  adminOnly() = " + svc.adminOnly());

        MeasurementLog.section("(3) SpEL — 본인 데이터만");
        setAuth("alice@example.com", "ROLE_USER");
        System.out.println("  mine(alice) = " + svc.mine("alice@example.com"));
        try {
            svc.mine("bob@example.com");
            System.out.println("  💥 다른 사용자 데이터 통과");
        } catch (AccessDeniedException e) {
            System.out.println("  ✓ mine(bob) 거부 — 본인 아님");
        }

        System.out.println();
        System.out.println("[학습]");
        System.out.println("  · @PreAuthorize = 5 주차 @Aspect 와 같은 AOP 프록시");
        System.out.println("  · SpEL — hasRole / authentication / 메서드 인자 자유");
        System.out.println("  · self-invocation 함정 → Stage3_2");
        ctx.close();
    }

    private static void setAuth(String name, String role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                name, null, AuthorityUtils.createAuthorityList(role)));
    }
}
