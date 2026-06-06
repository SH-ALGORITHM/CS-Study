package stage.s1;

import domain.Product;
import domain.ProductRepository;
import infra.MeasurementLog;
import infra.Seeder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 1-1 — 캐시 없이. 같은 id 100 회 조회 → SQL 100 회 발행.
 *
 * <h3>관찰 포인트</h3>
 * SQL 로그 (show-sql=true) 에서 SELECT * FROM product WHERE id=? 가 100 회 반복.
 */
@SpringBootApplication(scanBasePackages = {"stage.s1", "domain", "infra"})
public class Stage1_1_NoCache {

    @Service
    public static class ProductService {
        private final ProductRepository repo;
        public ProductService(ProductRepository repo) { this.repo = repo; }

        @Transactional(readOnly = true)
        public Product findById(Long id) {
            return repo.findById(id).orElseThrow();
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1_1_NoCache.class, args);
        ctx.getBean(Seeder.class).seed(10);
        ProductService svc = ctx.getBean(ProductService.class);

        MeasurementLog.title("STAGE 1-1 — 캐시 없이 같은 id 5 회 조회");
        long t1 = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            svc.findById(1L);
        }
        long ms = (System.nanoTime() - t1) / 1_000_000;
        System.out.println();
        System.out.println("[측정] 5 회 호출 = " + ms + "ms (SQL 5 회 발행)");
        ctx.close();
    }
}
