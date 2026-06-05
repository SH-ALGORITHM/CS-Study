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
 * STAGE 2-2 — JPQL JOIN FETCH 해결.
 *
 * <h3>해결</h3>
 * PostRepository.findAllWithCommentsJoinFetch() — "select distinct p from Post p join fetch p.comments"
 *
 * <h3>예상 SQL 로그</h3>
 * SELECT 1 회 (LEFT OUTER JOIN). 결과 행은 N×M (Cartesian) 인데 Hibernate 가 자바 객체 매핑 시 중복 제거.
 *
 * <h3>한계</h3>
 * <ul>
 *   <li>페이징 + 1:N 컬렉션 동시 = HHH000104 WARN + 메모리 페이징 (Stage2_5)</li>
 *   <li>컬렉션 2 개 동시 fetch = MultipleBagFetchException (Stage2_5)</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s2", "domain", "infra"})
public class Stage2_2_JoinFetch {

    @Service
    public static class JoinFetchDemo {
        private final PostRepository postRepo;
        public JoinFetchDemo(PostRepository postRepo) { this.postRepo = postRepo; }

        @Transactional(readOnly = true)
        public void listPosts() {
            MeasurementLog.section("postRepo.findAllWithCommentsJoinFetch() — SELECT 1 회 JOIN");
            List<Post> posts = postRepo.findAllWithCommentsJoinFetch();

            MeasurementLog.section("post.getComments() 접근 — 캐시, SELECT 추가 없음");
            for (Post p : posts) {
                int count = p.getComments().size();
                System.out.println("  " + p.getTitle() + " — 댓글 " + count);
            }
            System.out.println();
            System.out.println("  총 SQL = 1 회 (N+1 해소)");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_2_JoinFetch.class, args);
        ctx.getBean(SchemaSeeder.class).seed(10, 3);

        MeasurementLog.title("STAGE 2-2 — JPQL JOIN FETCH 해결");
        ctx.getBean(JoinFetchDemo.class).listPosts();

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · SQL 로그에서 SELECT 1 회만 발행 + LEFT OUTER JOIN 구조 확인");
        System.out.println("  · Hibernate 6.1+ 부터 1:N fetch join 시 distinct 없이도 자동 중복 제거");
        System.out.println("    → JPQL 의 'distinct' 키워드를 빼도 결과 같음 (SQL 에도 DISTINCT 안 들어감)");
        System.out.println("  · 결과 행 수 = N×M 이지만 자바 객체는 N 개로 묶임");
        ctx.close();
    }
}
