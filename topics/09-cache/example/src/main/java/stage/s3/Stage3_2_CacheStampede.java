package stage.s3;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import infra.MeasurementLog;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

/**
 * STAGE 3-2 — Cache stampede 재현. TTL 만료 직후 동시 100 요청 → 외부 호출 100 회.
 *
 * <h3>해결 방법</h3>
 * <ul>
 *   <li>(a) TTL + jitter — 만료 시간 ±20% 무작위</li>
 *   <li>(b) 분산락 (3 주차) — 한 요청만 외부 호출</li>
 *   <li>(c) @Cacheable(sync = true) — 단일 JVM 한정. 같은 키 동시 miss 시 한 스레드만</li>
 *   <li>(d) Caffeine refreshAfterWrite — 만료 전 미리 백그라운드 갱신</li>
 * </ul>
 *
 * <h3>이 데모는 (c) sync=true 효과를 직접 비교</h3>
 */
@SpringBootApplication(scanBasePackages = {"stage.s3", "infra"})
@EnableCaching
public class Stage3_2_CacheStampede {

    @Bean
    public CaffeineCacheManager cacheManager() {
        // ★ Bad / Good 가 캐시 공유하면 한 쪽 결과가 다른 쪽 HIT 일으킴 → 분리
        CaffeineCacheManager mgr = new CaffeineCacheManager("rates-bad", "rates-good");
        mgr.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMillis(100))     // 짧은 TTL 로 시연
        );
        return mgr;
    }

    @Service
    public static class RateServiceBad {
        private final AtomicInteger externalCalls = new AtomicInteger(0);

        @Cacheable(value = "rates-bad", key = "#currency")
        public String getRate(String currency) {
            int n = externalCalls.incrementAndGet();
            simulateExternalApi();
            return "USD-" + n;
        }

        public int externalCalls() { return externalCalls.get(); }
        public void reset() { externalCalls.set(0); }
    }

    @Service
    public static class RateServiceGood {
        private final AtomicInteger externalCalls = new AtomicInteger(0);

        // ★ sync = true — 같은 키 동시 miss 시 한 스레드만 (단일 JVM)
        @Cacheable(value = "rates-good", key = "#currency", sync = true)
        public String getRate(String currency) {
            int n = externalCalls.incrementAndGet();
            simulateExternalApi();
            return "USD-" + n;
        }

        public int externalCalls() { return externalCalls.get(); }
        public void reset() { externalCalls.set(0); }
    }

    private static void simulateExternalApi() {
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void runStampede(String label, java.util.function.Consumer<String> fn) throws InterruptedException {
        int n = 100;
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                    fn.accept("USD");
                });
            }
            ready.await();
            go.countDown();           // 100 스레드 동시 출발
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        System.out.println("  [" + label + "] 100 동시 요청 후 외부 호출 수 측정");
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_2_CacheStampede.class, args);

        MeasurementLog.title("STAGE 3-2 — Cache stampede 재현 + sync=true 해결");

        RateServiceBad bad = ctx.getBean(RateServiceBad.class);
        RateServiceGood good = ctx.getBean(RateServiceGood.class);

        MeasurementLog.section("(1) sync=false (기본) — 100 동시 → 외부 다수 회 호출 (stampede)");
        bad.reset();
        runStampede("Bad", bad::getRate);
        System.out.println("  외부 호출 수 = " + bad.externalCalls()
            + " (이상적으로 100 에 근접. 스레드 스케줄링에 따라 변동)");

        MeasurementLog.section("(2) sync=true — 100 동시 → 한 스레드만 외부 호출");
        good.reset();
        runStampede("Good", good::getRate);
        System.out.println("  외부 호출 수 = " + good.externalCalls() + " (1 회 기대)");

        System.out.println();
        System.out.println("[학습] 단일 JVM 한정 sync=true 로 해결. 다중 JVM 은 분산락 (3 주차) 또는 jitter");
        System.out.println("       Bad / Good 는 서로 다른 캐시 (rates-bad / rates-good) 라 결과 격리");
        ctx.close();
    }
}
