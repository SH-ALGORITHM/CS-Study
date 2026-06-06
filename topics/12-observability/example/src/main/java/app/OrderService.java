package app;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * 모든 커스텀 메트릭 시연 — Counter / Gauge / Timer / @Timed.
 *
 * <h3>메트릭</h3>
 * <ul>
 *   <li>orders_total{result="success|failure"} (Counter)</li>
 *   <li>active_orders (Gauge)</li>
 *   <li>order_duration → order_duration_seconds_* (Micrometer 가 _seconds 자동 접미사)</li>
 *   <li>timed_method   → timed_method_seconds_*   (@Timed AOP)</li>
 * </ul>
 *
 * <h3>이름 컨벤션</h3>
 * Timer 의 base 이름에 `_seconds` 직접 안 붙임 — Micrometer Prometheus 가 자동 추가.
 * 직접 `_seconds` 붙이면 일부 버전에서 중복 가능성 — 안전한 컨벤션.
 * <ul>
 * </ul>
 */
@Service
public class OrderService {

    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer orderTimer;
    private final AtomicInteger activeOrders = new AtomicInteger(0);

    public OrderService(MeterRegistry registry) {
        this.successCounter = Counter.builder("orders_total")
            .tag("result", "success")
            .description("성공한 주문 수")
            .register(registry);
        this.failureCounter = Counter.builder("orders_total")
            .tag("result", "failure")
            .register(registry);

        // ★ Gauge 가 참조하는 객체는 GC 안 되어야 — 필드로 유지
        Gauge.builder("active_orders", activeOrders, AtomicInteger::get)
            .description("현재 처리 중 주문 수")
            .register(registry);

        this.orderTimer = Timer.builder("order_duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            // ★ publishPercentileHistogram() — _bucket 시계열 노출 → PromQL histogram_quantile() 사용 가능
            //   publishPercentiles 만 켜면 앱 계산 quantile (order_duration_seconds{quantile=...}) 만
            //   노출 — Prometheus 서버측 집계 불가. 둘 다 켜는 게 학습용 표준
            .publishPercentileHistogram()
            .description("주문 처리 시간")
            .register(registry);
    }

    public String placeOrder(boolean fail) {
        // Timer.record — 람다가 예외 던져도 시간 + count 기록 (성공/실패 모두 count 포함)
        return orderTimer.record(() -> {
            activeOrders.incrementAndGet();
            try {
                simulateWork();
                if (fail) {
                    failureCounter.increment();
                    throw new RuntimeException("주문 실패");
                }
                successCounter.increment();
                return "ok";
            } finally {
                // finally 의 Gauge 감소 — 예외 시에도 active_orders 새지 않음 (좋은 패턴)
                activeOrders.decrementAndGet();
            }
        });
    }

    /** @Timed — 5 주차 @Aspect 같은 AOP 메커니즘 */
    @Timed(value = "timed_method", percentiles = {0.5, 0.95, 0.99})
    public String timedMethod() {
        simulateWork();
        return "ok";
    }

    private static void simulateWork() {
        try {
            // 응답 시간 분포 시연 — 10 ~ 100ms 무작위
            Thread.sleep((long) (10 + Math.random() * 90));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
