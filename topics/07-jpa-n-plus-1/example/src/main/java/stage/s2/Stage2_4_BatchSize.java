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
 * STAGE 2-4 — @BatchSize 해결. fetch join 의 한계를 회피.
 *
 * <h3>해결 — 전역 default_batch_fetch_size 켜기</h3>
 * application.properties 에서 주석 해제:
 * <pre>
 * spring.jpa.properties.hibernate.default_batch_fetch_size=100
 * </pre>
 * 또는 Post.comments 필드에 @BatchSize(size = 100) — 100 개씩 IN 묶음.
 *
 * <h3>예상 SQL 로그 (Post 10 + 전역 batch=100)</h3>
 * <pre>
 *   SELECT * FROM post                                        -- 1
 *   SELECT * FROM comment WHERE post_id IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)  -- 2
 *   총 2 회 (1 + ⌈10/100⌉ = 1 + 1)
 * </pre>
 *
 * <h3>학습 방법 — 두 경로</h3>
 * 1. 먼저 그대로 실행 — N+1 재현 (1 + 10 회 SQL, Stage2_1 과 동일)
 * 2. <b>경로 (A) 필드 어노테이션</b> — Post.java 의 @BatchSize 주석 해제 후 재실행
 * 3. <b>경로 (B) 전역 프로퍼티</b> — application.properties 의 default_batch_fetch_size 주석 해제 후 재실행
 * 4. 둘 다 SQL 로그에서 "post_id IN (?, ?, ...)" 한 줄로 묶이는지 확인
 *
 * <h3>fetch join 과 비교</h3>
 * <ul>
 *   <li>fetch join = 1 회. BatchSize = 1 + ⌈N/size⌉ 회</li>
 *   <li>BUT BatchSize 는 페이징 OK + 컬렉션 2 개 동시 OK (MultipleBagFetchException 회피)</li>
 *   <li>실무 권장 — 컬렉션 1 개 fetch join + 나머지 BatchSize. 또는 전역 default_batch_fetch_size</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s2", "domain", "infra"})
public class Stage2_4_BatchSize {

    @Service
    public static class BatchSizeDemo {
        private final PostRepository postRepo;
        public BatchSizeDemo(PostRepository postRepo) { this.postRepo = postRepo; }

        @Transactional(readOnly = true)
        public void listPosts() {
            MeasurementLog.section("postRepo.findAll() — @BatchSize 효과 확인");
            List<Post> posts = postRepo.findAll();

            MeasurementLog.section("각 post.getComments().size() — IN 절 묶음 fetch");
            for (Post p : posts) {
                int count = p.getComments().size();
                System.out.println("  " + p.getTitle() + " — 댓글 " + count);
            }
            System.out.println();
            System.out.println("  총 SQL = 1 + ⌈N / BatchSize⌉ 회 (Post 10 + BatchSize 100 = 2 회)");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_4_BatchSize.class, args);
        ctx.getBean(SchemaSeeder.class).seed(10, 3);

        MeasurementLog.title("STAGE 2-4 — @BatchSize 해결 (IN 절 묶음)");
        ctx.getBean(BatchSizeDemo.class).listPosts();

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · 그대로 실행 시 N+1 그대로 (전역 batch 설정 OFF 상태)");
        System.out.println("  · application.properties 의 default_batch_fetch_size 주석 해제 후 재실행");
        System.out.println("  · SQL 로그에서 SELECT comment WHERE post_id IN (?, ?, ...) 1 회 확인");
        System.out.println("  · 결과 행 중복 없음 (fetch join 의 Cartesian product 회피)");
        System.out.println("  · 페이징 + 컬렉션 2 개 동시 OK — fetch join 의 한계 모두 회피");
        ctx.close();
    }
}
