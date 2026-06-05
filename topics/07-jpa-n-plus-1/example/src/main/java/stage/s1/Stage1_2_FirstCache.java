package stage.s1;

import domain.Author;
import domain.AuthorRepository;
import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 1-2 — 1 차 캐시 + 동일성 보장.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>같은 트랜잭션 안 같은 id findById — 첫 회만 SELECT, 두 번째는 캐시</li>
 *   <li>a == b → true (동일성 보장)</li>
 *   <li>SQL 로그에 SELECT 가 1 회만 찍히는지 확인</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s1", "domain", "infra"})
public class Stage1_2_FirstCache {

    @Service
    public static class FirstCacheDemo {
        private final AuthorRepository repo;
        public FirstCacheDemo(AuthorRepository repo) { this.repo = repo; }

        // seed 는 별도 트랜잭션 — 이 트랜잭션이 끝나면서 영속성 컨텍스트 close.
        // 그 후 demo() 의 새 트랜잭션 / 새 영속성 컨텍스트에서 첫 findById = 진짜 SELECT.
        @Transactional
        public Long seed() {
            return repo.save(new Author("작성자")).getId();
        }

        @Transactional
        public void demo(Long id) {
            MeasurementLog.section("(1) 첫 findById — SELECT 발행");
            Author a = repo.findById(id).orElseThrow();
            MeasurementLog.marker("[A] 첫 조회 끝");

            MeasurementLog.section("(2) 두 번째 findById — 1 차 캐시 hit, SELECT 없음");
            Author b = repo.findById(id).orElseThrow();
            MeasurementLog.marker("[B] 두 번째 조회 끝");

            System.out.println();
            System.out.println("[동일성] a == b ? " + (a == b));
            System.out.println("[동일성] a.equals(b) ? " + a.equals(b));
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1_2_FirstCache.class, args);
        FirstCacheDemo svc = ctx.getBean(FirstCacheDemo.class);

        MeasurementLog.title("STAGE 1-2 — 1 차 캐시 + 동일성 보장");

        // ★ seed 와 demo 를 별도 트랜잭션으로 분리 — 그래야 첫 findById 에서 진짜 SELECT 발행
        Long id = svc.seed();
        MeasurementLog.marker("[Setup] author saved id=" + id + " (seed 트랜잭션 종료 → 영속성 컨텍스트 close)");

        svc.demo(id);

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · 두 번째 findById 사이에 SELECT 로그 없는지 SQL 로그로 확인");
        System.out.println("  · a == b → true (자바 ==. JdbcTemplate 시절엔 항상 새 객체)");
        ctx.close();
    }
}
