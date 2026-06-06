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
 * STAGE 3-2 — @PreAuthorize self-invocation 함정 (5, 6, 7, 9 주차 회수).
 *
 * <h3>두 시나리오</h3>
 * <ol>
 *   <li>BadService — this.adminOnly() 호출 → AOP 우회 → 권한 검사 X</li>
 *   <li>GoodWrapper → AdminService — 다른 빈 호출 → AOP 거침 → 권한 검사 O</li>
 * </ol>
 *
 * <h3>같은 메커니즘</h3>
 * 5 주차 @Transactional / 6 주차 @Async / 7 주차 readOnly / 9 주차 @Cacheable 모두 동일.
 *
 * <h3>거부 예외</h3>
 * AccessDeniedException (Spring 6.1+ 에서는 그 하위 AuthorizationDeniedException 가능)
 */
@SpringBootApplication
@EnableMethodSecurity
public class Stage3_2_SelfInvocation {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .csrf(c -> c.disable())
            .build();
    }

    @Bean public BadService badService() { return new BadService(); }
    @Bean public AdminService adminService() { return new AdminService(); }
    @Bean public GoodWrapper goodWrapper(AdminService a) { return new GoodWrapper(a); }

    public static class BadService {
        @PreAuthorize("hasRole('ADMIN')")
        public String adminOnly() {
            return "admin data (bad)";
        }

        public String wrapper() {
            // ★ this 호출 — AOP 우회 → 권한 검사 X
            return this.adminOnly();
        }
    }

    public static class AdminService {
        @PreAuthorize("hasRole('ADMIN')")
        public String adminOnly() {
            return "admin data (good)";
        }
    }

    public static class GoodWrapper {
        private final AdminService admin;
        public GoodWrapper(AdminService admin) { this.admin = admin; }

        public String wrapper() {
            // ★ 다른 빈 호출 — AOP 거침 → 권한 검사 O
            return this.admin.adminOnly();
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_2_SelfInvocation.class, args);

        MeasurementLog.title("STAGE 3-2 — @PreAuthorize self-invocation (5,6,7,9 주차 회수)");

        // ROLE_USER 로 인증 (ADMIN 아님)
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "alice", null, AuthorityUtils.createAuthorityList("ROLE_USER")));

        BadService bad = ctx.getBean(BadService.class);
        GoodWrapper good = ctx.getBean(GoodWrapper.class);

        MeasurementLog.section("(1) BadService.wrapper() — this.adminOnly() → AOP 우회");
        try {
            String result = bad.wrapper();
            System.out.println("  💥 통과: " + result + "  ← USER 인데 ADMIN 메서드 호출됨!");
        } catch (AccessDeniedException e) {
            System.out.println("  거부: " + e.getMessage());
        }

        MeasurementLog.section("(2) GoodWrapper.wrapper() → admin.adminOnly() — AOP 거침");
        try {
            String result = good.wrapper();
            System.out.println("  💥 통과: " + result);
        } catch (AccessDeniedException e) {
            System.out.println("  ✓ 거부: " + e.getMessage());
        }

        System.out.println();
        System.out.println("[학습]");
        System.out.println("  · Bad — this 호출 = 원본 객체 직접 = AOP 우회 = 권한 검사 X");
        System.out.println("  · Good — 다른 빈 호출 = 프록시 거침 = 권한 검사 O");
        System.out.println("  · 5 주차 @Transactional / 6 주차 @Async / 7 주차 readOnly / 9 주차 @Cacheable 동일");
        System.out.println("  · 해결: 클래스 분리 / 자기 주입 (@Lazy)");
        ctx.close();
    }
}
