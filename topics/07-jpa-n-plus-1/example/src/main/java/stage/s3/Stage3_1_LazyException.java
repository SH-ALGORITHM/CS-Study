package stage.s3;

import domain.Post;
import domain.PostRepository;
import infra.MeasurementLog;
import infra.SchemaSeeder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 3-1 — LazyInitializationException 재현.
 *
 * <h3>시나리오</h3>
 * 트랜잭션 안에서 Post 조회 → 반환. 트랜잭션 밖 (호출자) 에서 post.getComments().size() 접근 → 폭발.
 *
 * <h3>OSIV 의 정확한 범위</h3>
 * OSIV (spring.jpa.open-in-view=true) 의 Lazy 보호는 <b>서블릿 요청 컨텍스트 안에서만</b> 동작
 * (DispatcherServlet → OSIV 인터셉터가 세션 열고 응답 끝까지 유지). 이 데모는 main 에서 직접 호출이라
 * OSIV 필터 자체가 안 동작 → <b>OSIV ON 이어도 여기선 폭발이 정답</b>.
 *
 * <h3>해결 5 가지</h3>
 * <ul>
 *   <li>(a) DTO 변환 — 가장 권장 (Stage3_2)</li>
 *   <li>(b) JOIN FETCH / @EntityGraph — 미리 가져오기</li>
 *   <li>(c) Hibernate.initialize() — 강제 초기화</li>
 *   <li>(d) OSIV ON + 웹 요청 안에서만 — 학습 편의용. 운영 위험 (커넥션 풀 점유)</li>
 *   <li>(e) @Transactional 범위 확대 — 컨트롤러까지 (안티패턴)</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s3", "domain", "infra"})
public class Stage3_1_LazyException {

    @Service
    public static class LazyDemo {
        private final PostRepository postRepo;
        public LazyDemo(PostRepository postRepo) { this.postRepo = postRepo; }

        @Transactional
        public Post fetchAndReturn(Long id) {
            return postRepo.findById(id).orElseThrow();
            // 메서드 종료 → 영속성 컨텍스트 close → post 는 준영속
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_1_LazyException.class, args);
        ctx.getBean(SchemaSeeder.class).seed(3, 2);

        MeasurementLog.title("STAGE 3-1 — LazyInitializationException 재현");

        LazyDemo demo = ctx.getBean(LazyDemo.class);
        Post post = demo.fetchAndReturn(1L);

        MeasurementLog.section("트랜잭션 밖에서 Lazy 컬렉션 접근 — main 직접 호출 (비웹)");
        System.out.println("  OSIV ON 이어도 main 직접 호출 데모에서는 OSIV 필터가 안 동작 → 폭발 기대");
        try {
            int count = post.getComments().size();
            System.out.println("  댓글 수: " + count + " (환경에 따라 결과 다를 수 있음)");
        } catch (Exception ex) {
            System.out.println("  💥 " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · OSIV 의 Lazy 보호는 서블릿 요청 컨텍스트 안에서만 — DispatcherServlet 거쳐야 동작");
        System.out.println("  · main 에서 직접 호출 / @Async 별 스레드 / 배치 작업 → OSIV 와 무관하게 폭발");
        System.out.println("  · 운영에서도 OSIV OFF 권장 — 외부 API 대기 시 커넥션 풀 점유 → 전체 장애");
        System.out.println("  · 해결 권장: DTO 변환 (Stage3_2) — 트랜잭션 안에서 끝내기");
        ctx.close();
    }
}
