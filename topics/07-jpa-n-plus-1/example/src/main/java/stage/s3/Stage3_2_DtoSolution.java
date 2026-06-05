package stage.s3;

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
 * STAGE 3-2 — DTO 변환으로 Lazy 함정 회피.
 *
 * <h3>패턴</h3>
 * 트랜잭션 안에서 Entity → DTO 변환 후 반환. 호출자는 DTO 만 다룸 → Lazy 접근 자체가 없음.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>OSIV OFF 환경에서도 안전</li>
 *   <li>커넥션을 서비스 계층에서만 점유 (운영 권장)</li>
 *   <li>Entity 의 변경 감지 / 영속성 문제도 격리</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s3", "domain", "infra"})
public class Stage3_2_DtoSolution {

    public record PostSummary(Long id, String title, int commentCount) {
        public static PostSummary from(Post post) {
            return new PostSummary(post.getId(), post.getTitle(), post.getComments().size());
        }
    }

    @Service
    public static class PostService {
        private final PostRepository postRepo;
        public PostService(PostRepository postRepo) { this.postRepo = postRepo; }

        @Transactional(readOnly = true)
        public List<PostSummary> list() {
            // 트랜잭션 안에서 변환 — Lazy 접근 OK
            return postRepo.findAllWithCommentsJoinFetch().stream()
                .map(PostSummary::from)
                .toList();
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_2_DtoSolution.class, args);
        ctx.getBean(SchemaSeeder.class).seed(5, 2);

        MeasurementLog.title("STAGE 3-2 — DTO 변환으로 Lazy 함정 회피");

        List<PostSummary> summaries = ctx.getBean(PostService.class).list();

        MeasurementLog.section("호출자는 DTO 만 — Lazy 접근 없음");
        for (PostSummary s : summaries) {
            System.out.println("  " + s);
        }

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · 트랜잭션 안에서 Entity → DTO 변환 → 호출자는 안전한 record 만");
        System.out.println("  · OSIV OFF 환경에서도 동작 — 운영 권장 패턴");
        System.out.println("  · Entity 의 영속성 / 변경 감지가 컨트롤러로 새지 않음 (격리)");
        ctx.close();
    }
}
