package stage.s2;

import domain.Product;
import domain.ProductRepository;
import infra.MeasurementLog;
import infra.Seeder;
import java.math.BigDecimal;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 2-3 — @CachePut — 결과를 캐시에 강제 저장 (메서드는 항상 실행).
 *
 * <h3>@Cacheable vs @CachePut vs @CacheEvict</h3>
 * <ul>
 *   <li>@Cacheable — 캐시 있으면 메서드 실행 X</li>
 *   <li>@CachePut — 메서드 항상 실행 + 결과 캐시</li>
 *   <li>@CacheEvict — 캐시 비움 (다음 miss 시 새로)</li>
 * </ul>
 *
 * <h3>@CachePut vs @CacheEvict 트레이드오프</h3>
 * <ul>
 *   <li>@CachePut — update 직후 같은 id 조회 = HIT (방금 저장). 캐시 즉시 갱신</li>
 *   <li>@CacheEvict — update 직후 같은 id 조회 = MISS → DB → 캐시. 다음 조회 시점 갱신</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s2", "domain", "infra"})
@EnableCaching
public class Stage2_3_CachePut {

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

        @CachePut(value = "products", key = "#result.id")
        @Transactional
        public Product updatePrice(Long id, BigDecimal newPrice) {
            Product p = repo.findById(id).orElseThrow();
            p.setPrice(newPrice);
            MeasurementLog.marker("[PUT] cache updated — id=" + id);
            return p;
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_3_CachePut.class, args);
        ctx.getBean(Seeder.class).seed(5);
        ProductService svc = ctx.getBean(ProductService.class);

        MeasurementLog.title("STAGE 2-3 — @CachePut");

        MeasurementLog.section("(1) 첫 findById — MISS");
        System.out.println("  " + svc.findById(1L));

        MeasurementLog.section("(2) updatePrice — @CachePut 발동 (메서드 실행 + 캐시 갱신)");
        svc.updatePrice(1L, BigDecimal.valueOf(99999));

        MeasurementLog.section("(3) findById — HIT! 방금 저장된 값 (MISS 마커 없음)");
        System.out.println("  " + svc.findById(1L));

        System.out.println();
        System.out.println("[학습] @CacheEvict (다음 조회 시 갱신) vs @CachePut (즉시 갱신)");
        ctx.close();
    }
}
