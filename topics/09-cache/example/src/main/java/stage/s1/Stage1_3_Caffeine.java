package stage.s1;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import domain.Product;
import domain.ProductRepository;
import infra.MeasurementLog;
import infra.Seeder;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 1-3 — Caffeine 도입. maximumSize + expireAfterWrite + 통계.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>maximumSize 3 → 4 개째 추가 시 LRU 로 가장 오래 안 쓴 것 제거</li>
 *   <li>recordStats() + stats() → hit ratio / eviction count 확인</li>
 *   <li>expireAfterWrite 로 TTL 자동 처리</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s1", "domain", "infra"})
public class Stage1_3_Caffeine {

    @Service
    public static class ProductService {
        private final ProductRepository repo;
        private final Cache<Long, Product> cache = Caffeine.newBuilder()
            .maximumSize(3)                                  // 3 개만 보관 (LRU 시연용)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats()
            .build();

        public ProductService(ProductRepository repo) { this.repo = repo; }

        @Transactional(readOnly = true)
        public Product findById(Long id) {
            return cache.get(id, k -> {
                MeasurementLog.marker("[MISS] DB hit — id=" + k);
                return repo.findById(k).orElseThrow();
            });
        }

        public CacheStats stats() { return cache.stats(); }
        public long size() { return cache.estimatedSize(); }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1_3_Caffeine.class, args);
        ctx.getBean(Seeder.class).seed(10);
        ProductService svc = ctx.getBean(ProductService.class);

        MeasurementLog.title("STAGE 1-3 — Caffeine (maximumSize=3 + LRU + stats)");

        MeasurementLog.section("id 1, 2, 3 조회 — 3 개 모두 캐시");
        svc.findById(1L);
        svc.findById(2L);
        svc.findById(3L);
        System.out.println("[측정] 캐시 크기 = " + svc.size());

        MeasurementLog.section("id 4 추가 → LRU 로 가장 오래된 id 1 제거");
        svc.findById(4L);
        System.out.println("[측정] 캐시 크기 = " + svc.size() + " (3 유지)");

        MeasurementLog.section("id 1 다시 조회 → MISS (eviction 됐음)");
        svc.findById(1L);

        System.out.println();
        System.out.println("[통계]");
        CacheStats stats = svc.stats();
        System.out.println("  hitCount = " + stats.hitCount());
        System.out.println("  missCount = " + stats.missCount());
        System.out.println("  hitRate = " + String.format("%.2f%%", stats.hitRate() * 100));
        System.out.println("  evictionCount = " + stats.evictionCount());
        ctx.close();
    }
}
