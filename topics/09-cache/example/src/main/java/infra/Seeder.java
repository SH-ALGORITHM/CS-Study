package infra;

import domain.Product;
import domain.ProductRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Seeder {
    private final ProductRepository repo;
    public Seeder(ProductRepository repo) { this.repo = repo; }

    @Transactional
    public void seed(int n) {
        repo.deleteAllInBatch();
        for (int i = 1; i <= n; i++) {
            repo.save(new Product("Product #" + i, BigDecimal.valueOf(1000L + i)));
        }
        System.out.println("[Seed] " + n + " products");
    }
}
