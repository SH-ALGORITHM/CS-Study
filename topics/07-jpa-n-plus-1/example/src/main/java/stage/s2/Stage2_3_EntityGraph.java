package stage.s2;

import domain.Post;
import domain.PostRepository;
import infra.MeasurementLog;
import infra.SchemaSeeder;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 2-3 — @EntityGraph — Spring Data 선언형 fetch.
 *
 * <h3>정석 사용법</h3>
 * @EntityGraph 는 <b>JPQL 없이</b> derived query (findBy... / findAll 오버라이드) 에 얹는 게 정석.
 * @Query 와 함께 쓰면 Hibernate 6 / Spring Data 3 에서 LEFT JOIN FETCH 가 안 나갈 수 있음.
 *
 * <pre>
 * // ✗ 권장 X — JPQL 과 동시 사용
 * @EntityGraph(attributePaths = {"comments"})
 * @Query("select p from Post p")
 * List&lt;Post&gt; findAll();
 *
 * // ✓ 권장 — derived query 에만
 * @EntityGraph(attributePaths = {"comments"})
 * List&lt;Post&gt; findAllByOrderByIdAsc();
 * </pre>
 *
 * <h3>JPQL JOIN FETCH 와 차이</h3>
 * <ul>
 *   <li>JPQL 안 짜고도 동일 효과 (LEFT JOIN FETCH)</li>
 *   <li>복잡한 JOIN 조건은 JPQL 이 유연. 단순한 경우 @EntityGraph 권장</li>
 *   <li>여러 연관 동시: attributePaths = {"comments", "author"} — 단 컬렉션 2 개는 MultipleBagFetchException</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s2", "domain", "infra"})
public class Stage2_3_EntityGraph {

    @Service
    public static class EntityGraphDemo {
        private final PostRepository postRepo;
        public EntityGraphDemo(PostRepository postRepo) { this.postRepo = postRepo; }

        @Transactional(readOnly = true)
        public void listPosts() {
            MeasurementLog.section("postRepo.findAllByOrderByIdAsc() — @EntityGraph (derived query)");
            List<Post> posts = postRepo.findAllByOrderByIdAsc();

            for (Post p : posts) {
                int count = p.getComments().size();
                System.out.println("  " + p.getTitle() + " — 댓글 " + count);
            }
            System.out.println();
            System.out.println("  총 SQL = 1 회 (JOIN FETCH 와 동일 효과)");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_3_EntityGraph.class, args);
        ctx.getBean(SchemaSeeder.class).seed(10, 3);

        MeasurementLog.title("STAGE 2-3 — @EntityGraph 선언형 fetch");
        ctx.getBean(EntityGraphDemo.class).listPosts();

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · JPQL 안 짜고 @EntityGraph 어노테이션만 — 메서드 시그니처 그대로");
        System.out.println("  · SQL 로그는 Stage2_2 와 동일 (LEFT OUTER JOIN)");
        System.out.println("  · 복잡한 조건은 JPQL / 단순하면 @EntityGraph 권장");
        ctx.close();
    }
}
