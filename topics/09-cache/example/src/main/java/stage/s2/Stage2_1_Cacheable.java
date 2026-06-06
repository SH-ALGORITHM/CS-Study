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
 * STAGE 2-1 — @Cacheable 한 줄로 손 캐시 (Stage1_3) 추출.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>Stage1_3 의 Caffeine 코드 (cache.get / build / stats) — 사라짐</li>
 *   <li>@Cacheable 한 줄로 끝. 5 주차 @Audited 와 같은 추출 패턴 (AOP 프록시)</li>
 *   <li>같은 id 5 회 → SELECT 1 회 + 캐시 4 회</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s2", "domain", "infra"})
@EnableCaching
public class Stage2_1_Cacheable {

    @Bean
    public ConcurrentMapCacheManager cacheManager() {
        // 기본 — HashMap 기반. 학습용. 운영은 Caffeine / Redis
        return new ConcurrentMapCacheManager("products");
    }

    @Service
    public static class ProductService {
        private final ProductRepository repo;
        public ProductService(ProductRepository repo) { this.repo = repo; }

        // @Transactional 일부러 안 붙임 — Spring Data JPA findById 가 자체 readOnly TX 제공.
        // @Cacheable + @Transactional 겹치면 HIT 시에도 빈 TX 가 열려 학습이 흐려지고,
        // self-invocation 시 둘 다 우회되어 관찰 의도가 망가짐.
        // (실무 — null 도 캐시되는 게 싫으면 unless = "#result == null" 추가)
        @Cacheable(value = "products", key = "#id")
        public Product findById(Long id) {
            MeasurementLog.marker("[MISS] DB hit — id=" + id);
            return repo.findById(id).orElseThrow();
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_1_Cacheable.class, args);
        ctx.getBean(Seeder.class).seed(10);
        ProductService svc = ctx.getBean(ProductService.class);

        MeasurementLog.title("STAGE 2-1 — @Cacheable 한 줄");
        MeasurementLog.section("같은 id 5 회 — MISS 마커 1 번만 나오는지 확인");
        for (int i = 0; i < 5; i++) svc.findById(1L);

        System.out.println();
        System.out.println("[학습] 5 주차 @Aspect 와 같은 추출. 단 한 줄로 캐시");
        ctx.close();
    }
}
