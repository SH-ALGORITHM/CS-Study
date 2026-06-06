package stage.s2;

import domain.Product;
import domain.ProductRepository;
import infra.MeasurementLog;
import infra.Seeder;
import java.math.BigDecimal;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 2-2 — @CacheEvict — 변경 시 캐시 무효화.
 *
 * <h3>시나리오</h3>
 * <ol>
 *   <li>findById(1) → MISS → DB → 캐시 저장</li>
 *   <li>findById(1) → HIT</li>
 *   <li>updatePrice(1, new) → @CacheEvict 로 캐시 비움</li>
 *   <li>findById(1) → MISS → DB → 새 값</li>
 * </ol>
 */
@SpringBootApplication(scanBasePackages = {"stage.s2", "domain", "infra"})
@EnableCaching
public class Stage2_2_CacheEvict {

    @Bean
    public ConcurrentMapCacheManager cacheManager() {
        return new ConcurrentMapCacheManager("products");
    }

    @Service
    public static class ProductService {
        private final ProductRepository repo;
        public ProductService(ProductRepository repo) { this.repo = repo; }

        @Cacheable(value = "products", key = "#id")
        public Product findById(Long id) {
            MeasurementLog.marker("[MISS] DB hit — id=" + id);
            return repo.findById(id).orElseThrow();
        }

        @CacheEvict(value = "products", key = "#id")
        @Transactional
        public void updatePrice(Long id, BigDecimal newPrice) {
            Product p = repo.findById(id).orElseThrow();
            p.setPrice(newPrice);
            repo.save(p);     // 7 주차 변경 감지로도 동작하나 학습 명확화 차 명시
            MeasurementLog.marker("[EVICT] cache cleared — id=" + id);
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_2_CacheEvict.class, args);
        ctx.getBean(Seeder.class).seed(5);
        ProductService svc = ctx.getBean(ProductService.class);

        MeasurementLog.title("STAGE 2-2 — @CacheEvict");

        MeasurementLog.section("(1) 첫 findById — MISS");
        System.out.println("  " + svc.findById(1L));

        MeasurementLog.section("(2) 같은 id 재조회 — HIT (MISS 마커 없음)");
        System.out.println("  " + svc.findById(1L));

        MeasurementLog.section("(3) updatePrice — @CacheEvict 발동");
        svc.updatePrice(1L, BigDecimal.valueOf(99999));

        MeasurementLog.section("(4) 다시 findById — MISS → 새 값");
        System.out.println("  " + svc.findById(1L));

        System.out.println();
        System.out.println("[학습] @CacheEvict 없으면 (3) 후에도 옛 값 = Stale read");
        ctx.close();
    }
}
