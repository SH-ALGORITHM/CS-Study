package stage.s2;

import domain.Product;
import domain.ProductRepository;
import infra.MeasurementLog;
import infra.Seeder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 2-5 — @Cacheable self-invocation 함정 (5, 6, 7 주차 회수).
 *
 * <h3>두 시나리오</h3>
 * <ol>
 *   <li>BadService — this.findById() 호출 → 프록시 우회 → 캐시 안 먹음 → 매번 MISS</li>
 *   <li>GoodService — 다른 빈 (CachedService) 의 findById() 호출 → 프록시 거침 → 캐시 동작</li>
 * </ol>
 *
 * <h3>5, 6, 7 주차와 같은 메커니즘</h3>
 * @Cacheable / @Transactional / @Async / @TransactionalEventListener 모두 동일.
 * this 호출 = 원본 객체 = 프록시 우회.
 */
@SpringBootApplication(scanBasePackages = {"stage.s2", "domain", "infra"})
@EnableCaching
public class Stage2_5_SelfInvocation {

    @Bean
    public ConcurrentMapCacheManager cacheManager() {
        return new ConcurrentMapCacheManager("products");
    }

    @Service
    public static class BadService {
        private final ProductRepository repo;
        public BadService(ProductRepository repo) { this.repo = repo; }

        // @Transactional 없이 — self-invocation 시 @Cacheable 우회만 순수하게 보여주려고
        @Cacheable(value = "products", key = "#id")
        public Product findById(Long id) {
            MeasurementLog.marker("  [Bad MISS] DB hit — id=" + id);
            return repo.findById(id).orElseThrow();
        }

        public void wrapper(Long id) {
            // ★ this 호출 = 프록시 우회 = 캐시 동작 X
            this.findById(id);
        }
    }

    @Service
    public static class CachedService {
        private final ProductRepository repo;
        public CachedService(ProductRepository repo) { this.repo = repo; }

        // @Transactional 없이 — self-invocation 시 @Cacheable 우회만 순수하게 보여주려고
        @Cacheable(value = "products", key = "#id")
        public Product findById(Long id) {
            MeasurementLog.marker("  [Good MISS] DB hit — id=" + id);
            return repo.findById(id).orElseThrow();
        }
    }

    @Service
    public static class GoodWrapper {
        private final CachedService cached;
        public GoodWrapper(CachedService cached) { this.cached = cached; }

        public void wrapper(Long id) {
            // ★ 다른 빈 호출 = 프록시 거침 = 캐시 동작 O
            this.cached.findById(id);
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_5_SelfInvocation.class, args);
        ctx.getBean(Seeder.class).seed(5);

        MeasurementLog.title("STAGE 2-5 — @Cacheable self-invocation 함정");

        MeasurementLog.section("(1) BadService — this 호출 5 회 → 매번 MISS");
        BadService bad = ctx.getBean(BadService.class);
        for (int i = 0; i < 5; i++) bad.wrapper(1L);

        MeasurementLog.section("(2) GoodWrapper → CachedService — 다른 빈 호출 5 회 → 첫 회만 MISS");
        GoodWrapper good = ctx.getBean(GoodWrapper.class);
        for (int i = 0; i < 5; i++) good.wrapper(1L);

        System.out.println();
        System.out.println("[학습] 5 주차 @Transactional / 6 주차 @Async 와 같은 프록시 메커니즘");
        System.out.println("       해결: 클래스 분리 / 자기 자신 주입 (@Lazy)");
        ctx.close();
    }
}
