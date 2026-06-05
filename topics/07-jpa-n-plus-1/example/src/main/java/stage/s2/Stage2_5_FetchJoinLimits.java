package stage.s2;

import domain.Post;
import domain.PostRepository;
import infra.MeasurementLog;
import infra.SchemaSeeder;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 2-5 — fetch join 한계: 페이징 + 컬렉션 동시.
 *
 * <h3>한계 — 버전에 따라 동작 다름</h3>
 * fetch join + Pageable 의 동작은 Hibernate 버전에 따라 다름. 직접 콘솔에서 확인:
 * <ul>
 *   <li>Hibernate 5.x — WARN "HHH000104: firstResult/maxResults specified with collection fetch; applying in memory" + 조용히 메모리 페이징</li>
 *   <li>Hibernate 6.x (현재) — WARN 코드 / 메시지 다를 수 있음 / 케이스에 따라 예외</li>
 * </ul>
 *
 * <h3>왜 위험한가 (공통)</h3>
 * 메모리 페이징이 발생하면 DB 에서 모든 Post + Comment 를 가져온 후 메모리에서 페이징.
 * Post 100 만 개면 OOM. 핵심 교훈은 "1:N fetch join + 페이징은 위험" 이라는 점.
 *
 * <h3>해결</h3>
 * <ul>
 *   <li>(a) 컬렉션 1 개만 fetch + 나머지 @BatchSize</li>
 *   <li>(b) 2-phase fetch: 1 단계 ID 페이징 / 2 단계 ID 로 fetch join</li>
 *   <li>(c) Pageable 없이 BatchSize 만</li>
 * </ul>
 *
 * <h3>또 다른 한계 — MultipleBagFetchException</h3>
 * 컬렉션 2 개 동시 fetch join 시도 (예: comments + tags) 는 Hibernate 가 매핑 불가 판단.
 * 이 stage 에서는 시연 안 함 (관련 Entity 가 1 개라). 본인 도메인 구현 시 직접 부딪힐 것.
 */
@SpringBootApplication(scanBasePackages = {"stage.s2", "domain", "infra"})
public class Stage2_5_FetchJoinLimits {

    @Service
    public static class FetchJoinLimitDemo {
        private final PostRepository postRepo;
        public FetchJoinLimitDemo(PostRepository postRepo) { this.postRepo = postRepo; }

        @Transactional(readOnly = true)
        public void runPaged() {
            MeasurementLog.section("fetch join + 페이징 — 버전별 동작 직접 확인");
            try {
                List<Post> posts = postRepo.findAllWithCommentsPaged(PageRequest.of(0, 3));
                System.out.println();
                System.out.println("  반환된 Post 수: " + posts.size() + " (요청: 3)");
                System.out.println("  ⚠️ 콘솔에서 WARN 메시지 확인 — 버전에 따라");
                System.out.println("     Hibernate 5.x: 'HHH000104: ... applying in memory'");
                System.out.println("     Hibernate 6.x: 다른 코드 / 메시지일 수 있음");
            } catch (Exception ex) {
                System.out.println("  💥 예외 발생: " + ex.getClass().getSimpleName());
                System.out.println("     " + ex.getMessage());
                System.out.println("  → Hibernate 6.x 의 일부 케이스에서 메모리 페이징 대신 예외");
            }
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_5_FetchJoinLimits.class, args);
        ctx.getBean(SchemaSeeder.class).seed(10, 3);

        MeasurementLog.title("STAGE 2-5 — fetch join 한계 (페이징 + 컬렉션)");
        ctx.getBean(FetchJoinLimitDemo.class).runPaged();

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · 핵심: 1:N fetch join + 페이징 = 위험 (메모리 페이징 또는 예외)");
        System.out.println("  · 정확한 WARN 코드 / 메시지는 버전 의존 — 콘솔에서 직접 확인");
        System.out.println("  · 운영 환경에서 메모리 페이징 발생 시 Post 100 만 개면 OOM");
        System.out.println("  · 해결: 컬렉션 1 개 fetch + 나머지 @BatchSize / 2-phase fetch");
        System.out.println("  · MultipleBagFetchException — 컬렉션 2 개 동시 fetch 시 발생 (본인 도메인에서 부딪힐 것)");
        ctx.close();
    }
}
