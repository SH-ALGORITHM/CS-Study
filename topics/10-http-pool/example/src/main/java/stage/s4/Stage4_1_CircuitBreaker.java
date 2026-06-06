package stage.s4;

import infra.MeasurementLog;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * STAGE 4-1 — Resilience4j Circuit Breaker.
 *
 * <h3>이 데모는 CircuitBreakerRegistry 로 프로그래밍 방식 호출</h3>
 * 본문 시나리오 4-1 은 @CircuitBreaker 어노테이션 방식으로 설명. 두 방식 결과 동일.
 * 이 데모는 학습 단순화를 위해 registry 직접 사용 — <b>5 주차 self-invocation 함정 회피</b>
 * (같은 클래스 안 호출 시 어노테이션 방식 = 캐시 / TX / Async 처럼 우회 동일).
 *
 * <h3>설정 (application.properties)</h3>
 * <ul>
 *   <li>failure-rate-threshold = 50 (%)</li>
 *   <li>sliding-window-size = 10 / minimum-number-of-calls = 5</li>
 *   <li>wait-duration-in-open-state = 5s</li>
 *   <li>permitted-number-of-calls-in-half-open-state = 3 (HALF_OPEN "간보기" 한도)</li>
 * </ul>
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>SlowApiServer /flaky 의 실패율 70% → 슬라이딩 윈도우 50% 임계치 안정 초과 → OPEN</li>
 *   <li>OPEN 상태에서 호출 시 즉시 fallback (외부 호출 0)</li>
 *   <li>5 초 후 HALF_OPEN → 3 회 시도 → 성공률 따라 CLOSED / OPEN</li>
 * </ul>
 */
@SpringBootApplication
public class Stage4_1_CircuitBreaker {

    @Bean
    public RestClient restClient() {
        return RestClient.builder().baseUrl("http://localhost:8081").build();
    }

    @Service
    public static class PaymentService {
        private final RestClient client;
        private final CircuitBreaker circuitBreaker;

        public PaymentService(RestClient client, CircuitBreakerRegistry registry) {
            this.client = client;
            this.circuitBreaker = registry.circuitBreaker("slowapi");
        }

        public String charge(int requestId, String endpoint) {
            try {
                return circuitBreaker.executeSupplier(() ->
                    client.get().uri(endpoint).retrieve().body(String.class)
                );
            } catch (Exception e) {
                return "FALLBACK (" + e.getClass().getSimpleName() + ")";
            }
        }

        public CircuitBreaker.State getState() {
            return circuitBreaker.getState();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Stage4_1_CircuitBreaker.class).web(WebApplicationType.NONE).run(args);
        PaymentService svc = ctx.getBean(PaymentService.class);

        MeasurementLog.title("STAGE 4-1 — Circuit Breaker (Resilience4j)");

        MeasurementLog.section("(1) /flaky (70% 실패) 호출 20 회 — 실패율 따라 OPEN 전환 관찰");
        for (int i = 1; i <= 20; i++) {
            String result = svc.charge(i, "/flaky");
            System.out.printf("  [%2d] state=%s result=%s%n", i, svc.getState(), result);
        }

        MeasurementLog.section("(2) OPEN 상태에서 호출 → 즉시 fallback (외부 호출 0)");
        if (svc.getState() == CircuitBreaker.State.OPEN) {
            long t1 = System.nanoTime();
            String result = svc.charge(999, "/flaky");
            long ms = (System.nanoTime() - t1) / 1_000_000;
            System.out.println("  결과: " + result + " (" + ms + "ms — 빠른 실패)");
        }

        MeasurementLog.section("(3) 5 초 대기 후 HALF_OPEN → /flaky 시도 — 대개 재OPEN (70% 실패)");
        Thread.sleep(5500);
        for (int i = 1; i <= 3; i++) {
            String result = svc.charge(i, "/flaky");
            System.out.printf("  [%d] state=%s result=%s%n", i, svc.getState(), result);
        }

        MeasurementLog.section("(4) 다시 5 초 대기 후 HALF_OPEN — 이번엔 /fast (항상 성공) → CLOSED 복귀");
        Thread.sleep(5500);
        for (int i = 1; i <= 5; i++) {
            String result = svc.charge(i, "/fast");
            System.out.printf("  [%d] state=%s result=%s%n", i, svc.getState(), result);
        }

        System.out.println();
        System.out.println("[학습] CLOSED → 실패율 임계 초과 → OPEN → wait-duration 후 HALF_OPEN");
        System.out.println("       HALF_OPEN 에서 permitted N 회 만 시도 → 성공률 따라 CLOSED / 재OPEN");
        System.out.println("       OPEN 상태는 외부 호출 자체 안 함 → 톰캣 워커 보호");
        ctx.close();
    }
}
