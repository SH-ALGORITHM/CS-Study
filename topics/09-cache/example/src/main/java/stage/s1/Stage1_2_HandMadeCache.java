package stage.s1;

import domain.Product;
import domain.ProductRepository;
import infra.MeasurementLog;
import infra.Seeder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 1-2 — ConcurrentHashMap 손 캐시. TTL / 크기 제한 없음.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>같은 id 5 회 → SQL 1 회 + 캐시 4 회 hit</li>
 *   <li>다른 id 모두 캐시에 쌓이면 무한 증가 → 메모리 누수 위험</li>
 *   <li>DB 변경 시 캐시 안 비우면 옛 값 (Stale read)</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s1", "domain", "infra"})
public class Stage1_2_HandMadeCache {

    @Service
    public static class ProductService {
        private final ProductRepository repo;
        private final Map<Long, Product> cache = new ConcurrentHashMap<>();

        public ProductService(ProductRepository repo) { this.repo = repo; }

        @Transactional(readOnly = true)
        public Product findById(Long id) {
            return cache.computeIfAbsent(id, k -> {
                MeasurementLog.marker("[MISS] DB hit — id=" + k);
                return repo.findById(k).orElseThrow();
            });
        }

        public int cacheSize() { return cache.size(); }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1_2_HandMadeCache.class, args);
        ctx.getBean(Seeder.class).seed(10);
        ProductService svc = ctx.getBean(ProductService.class);

        MeasurementLog.title("STAGE 1-2 — ConcurrentHashMap 손 캐시");
        MeasurementLog.section("같은 id=1 을 5 회 조회 — DB 1 회 + 캐시 4 회");
        for (int i = 0; i < 5; i++) {
            svc.findById(1L);
        }
        System.out.println();
        System.out.println("[측정] 캐시 크기 = " + svc.cacheSize() + " (id 1 만 있음)");

        MeasurementLog.section("id 1 ~ 10 모두 조회 — 캐시 무한 증가");
        for (long i = 1; i <= 10; i++) svc.findById(i);
        System.out.println("[측정] 캐시 크기 = " + svc.cacheSize() + " (전체 캐시됨)");
        System.out.println("[학습] TTL / 사이즈 제한 없는 손 캐시 = 운영 시 OOM 위험. Caffeine 으로 (Stage1_3)");
        ctx.close();
    }
}
