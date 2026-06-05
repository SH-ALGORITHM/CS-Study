package stage.s4;

import domain.Author;
import domain.AuthorRepository;
import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 4-2 — Propagation.REQUIRES_NEW — 새 트랜잭션 + 새 영속성 컨텍스트.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>호출자 (REQUIRED) 와 nested (REQUIRES_NEW) 는 다른 트랜잭션</li>
 *   <li>다른 영속성 컨텍스트 → findById 결과 인스턴스가 다름 (== false)</li>
 *   <li>호출자가 변경한 내용은 commit 전이라 nested 에서 SELECT 시 안 보임 (격리 수준에 따라)</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s4", "domain", "infra"})
public class Stage4_2_RequiresNew {

    @Service
    public static class CallerService {
        private final AuthorRepository repo;
        private final NestedService nested;

        public CallerService(AuthorRepository repo, NestedService nested) {
            this.repo = repo;
            this.nested = nested;
        }

        @Transactional
        public void run(Long id) {
            Author a = repo.findById(id).orElseThrow();
            System.out.println("  [Caller] a.getName() = " + a.getName() + " hash=" + System.identityHashCode(a));

            // REQUIRES_NEW — 새 트랜잭션 + 새 영속성 컨텍스트
            nested.runInNew(id);

            Author a2 = repo.findById(id).orElseThrow();
            System.out.println("  [Caller] 다시 findById 후 a == a2 ? " + (a == a2)
                + "  (같은 트랜잭션이라 1 차 캐시 적중 → true)");
        }
    }

    @Service
    public static class NestedService {
        private final AuthorRepository repo;
        public NestedService(AuthorRepository repo) { this.repo = repo; }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void runInNew(Long id) {
            Author b = repo.findById(id).orElseThrow();
            System.out.println("  [Nested REQUIRES_NEW] b.getName() = " + b.getName()
                + " hash=" + System.identityHashCode(b));
            System.out.println("    → 새 트랜잭션 + 새 영속성 컨텍스트라 호출자의 a 와 다른 인스턴스");
        }
    }

    @Service
    public static class SeedService {
        private final AuthorRepository repo;
        public SeedService(AuthorRepository repo) { this.repo = repo; }

        @Transactional
        public Long seed() {
            return repo.save(new Author("작성자")).getId();
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_2_RequiresNew.class, args);

        MeasurementLog.title("STAGE 4-2 — REQUIRES_NEW 새 트랜잭션 + 새 영속성 컨텍스트");

        Long id = ctx.getBean(SeedService.class).seed();
        ctx.getBean(CallerService.class).run(id);

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · 호출자 (REQUIRED) 와 nested (REQUIRES_NEW) 는 다른 트랜잭션");
        System.out.println("  · 다른 영속성 컨텍스트 → a 와 b 의 instance hash 가 다름");
        System.out.println("  · 6 주차 @Async + AFTER_COMMIT 의 새 스레드와 같은 결 — 새 컨텍스트");
        ctx.close();
    }
}
