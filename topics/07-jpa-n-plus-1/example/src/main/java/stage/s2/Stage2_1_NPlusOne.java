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
 * STAGE 2-1 — N+1 재현. 7 주차 가장 중요한 학습.
 *
 * <h3>시나리오</h3>
 * Post 10 개 + 각 Post 마다 Comment 3 개. postRepo.findAll() 후 각 post.getComments().size() 호출.
 *
 * <h3>예상 SQL 로그</h3>
 * <pre>
 *   SELECT * FROM post                                       -- 1
 *   SELECT * FROM comment WHERE post_id = 1                  -- 2
 *   SELECT * FROM comment WHERE post_id = 2                  -- 3
 *   ... 총 11 회 (1+10)
 * </pre>
 *
 * <h3>해결</h3>
 * Stage2_2 (JOIN FETCH) / Stage2_3 (@EntityGraph) / Stage2_4 (@BatchSize)
 */
@SpringBootApplication(scanBasePackages = {"stage.s2", "domain", "infra"})
public class Stage2_1_NPlusOne {

    @Service
    public static class NPlusOneDemo {
        private final PostRepository postRepo;
        public NPlusOneDemo(PostRepository postRepo) { this.postRepo = postRepo; }

        @Transactional(readOnly = true)
        public void listPosts() {
            MeasurementLog.section("postRepo.findAll() — SELECT 1 회");
            List<Post> posts = postRepo.findAll();

            MeasurementLog.section("각 post.getComments().size() — Lazy fetch N 회");
            for (Post p : posts) {
                int count = p.getComments().size();
                System.out.println("  " + p.getTitle() + " — 댓글 " + count);
            }
            System.out.println();
            System.out.println("  총 SQL = 1 + " + posts.size() + " 회 (예상: " + (1 + posts.size()) + ")");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_1_NPlusOne.class, args);
        ctx.getBean(SchemaSeeder.class).seed(10, 3);

        MeasurementLog.title("STAGE 2-1 — N+1 재현 (Post 10 + Comment 30)");
        ctx.getBean(NPlusOneDemo.class).listPosts();

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · SQL 로그에서 SELECT comment WHERE post_id=? 가 10 회 반복되는지 확인");
        System.out.println("  · Post 100 개면 101 회 SQL → DB 50ms × 101 = 5 초");
        System.out.println("  · 측정 매트릭스 채우기: ctx.getBean(SchemaSeeder.class).seed(50, 3) / seed(100, 3) 으로");
        System.out.println("    main 의 seed() 인자를 바꿔서 재실행");
        System.out.println("  · 해결 → Stage2_2 (JOIN FETCH) / Stage2_3 (@EntityGraph) / Stage2_4 (@BatchSize)");
        ctx.close();
    }
}
