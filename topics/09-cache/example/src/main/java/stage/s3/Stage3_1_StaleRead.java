package stage.s3;

import domain.Product;
import domain.ProductRepository;
import infra.MeasurementLog;
import infra.Seeder;
import java.math.BigDecimal;
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
 * STAGE 3-1 — Stale read 재현. DB 변경 + @CacheEvict 없으면 옛 값.
 *
 * <h3>시나리오</h3>
 * <ol>
 *   <li>findById(1) → MISS → 캐시에 price=1001 저장</li>
 *   <li>updatePriceWithoutEvict(1, 99999) → DB 의 price 변경. 캐시 안 비움</li>
 *   <li>findById(1) → HIT → 옛 price=1001 반환 (Stale)</li>
 * </ol>
 */
@SpringBootApplication(scanBasePackages = {"stage.s3", "domain", "infra"})
@EnableCaching
public class Stage3_1_StaleRead {

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

        // ★ @CacheEvict 일부러 누락
        @Transactional
        public void updatePriceWithoutEvict(Long id, BigDecimal newPrice) {
            Product p = repo.findById(id).orElseThrow();
            p.setPrice(newPrice);
            repo.save(p);     // 7 주차 변경 감지로도 동작하나 학습 명확화 차 명시
            MeasurementLog.marker("[DB UPDATE] id=" + id + " price=" + newPrice + " — 캐시 안 비움");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_1_StaleRead.class, args);
        ctx.getBean(Seeder.class).seed(5);
        ProductService svc = ctx.getBean(ProductService.class);

        MeasurementLog.title("STAGE 3-1 — Stale read 재현");

        MeasurementLog.section("(1) findById(1) — MISS → 캐시 저장");
        System.out.println("  " + svc.findById(1L));

        MeasurementLog.section("(2) DB 만 변경 (price=99999). 캐시 invalidate 안 함");
        svc.updatePriceWithoutEvict(1L, BigDecimal.valueOf(99999));

        MeasurementLog.section("(3) 다시 findById(1) — HIT → 옛 값 반환!");
        Product stale = svc.findById(1L);
        System.out.println("  " + stale + "  ← DB 는 99999 인데 캐시는 옛 1001");

        System.out.println();
        System.out.println("[학습] 변경 메서드에는 반드시 @CacheEvict 명시");
        System.out.println("       또는 짧은 TTL 로 자연 만료 (Stale 허용 시간 만큼)");
        ctx.close();
    }
}
