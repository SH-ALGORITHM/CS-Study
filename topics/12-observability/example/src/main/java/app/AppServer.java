package app;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가짜 비즈니스 서비스 — 모든 메트릭 시연.
 *
 * <h3>실행</h3>
 * <pre>./gradlew run -PmainClass=app.AppServer</pre>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>GET /api/order?fail={true|false} — 주문 시뮬 (Counter / Gauge / Timer)</li>
 *   <li>GET /api/timed — @Timed 어노테이션 시연</li>
 *   <li>GET /actuator/health</li>
 *   <li>GET /actuator/metrics</li>
 *   <li>GET /actuator/prometheus</li>
 * </ul>
 */
@SpringBootApplication
@RestController
public class AppServer {

    private final OrderService orderService;

    public AppServer(OrderService orderService) {
        this.orderService = orderService;
    }

    /** @Timed 활성화 — 5 주차 @Aspect 같은 메커니즘 */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @GetMapping("/api/order")
    public String order(@RequestParam(defaultValue = "false") boolean fail) {
        return orderService.placeOrder(fail);
    }

    @GetMapping("/api/timed")
    public String timed() {
        return orderService.timedMethod();
    }

    public static void main(String[] args) {
        SpringApplication.run(AppServer.class, args);
        System.out.println();
        System.out.println("=== AppServer started on :8080 ===");
        System.out.println("  GET /api/order?fail=false  — 정상 (Counter / Timer / Gauge)");
        System.out.println("  GET /api/order?fail=true   — 실패 시뮬");
        System.out.println("  GET /api/timed             — @Timed 시연");
        System.out.println("  GET /actuator/prometheus   — Prometheus scrape 엔드포인트");
        System.out.println();
        System.out.println("부하 — 다른 터미널: ./gradlew run -PmainClass=app.LoadGenerator");
    }
}
