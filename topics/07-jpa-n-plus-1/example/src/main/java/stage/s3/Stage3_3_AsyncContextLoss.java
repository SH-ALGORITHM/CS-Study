package stage.s3;

import domain.Post;
import domain.PostRepository;
import infra.MeasurementLog;
import infra.SchemaSeeder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * STAGE 3-3 — @Async + @TransactionalEventListener(AFTER_COMMIT) 새 스레드 함정 (6 주차 회수).
 *
 * <h3>두 이벤트로 분리 (출력 섞임 방지)</h3>
 * BadListener / GoodListener 가 같은 이벤트를 구독하면 비동기라 출력이 섞임 →
 * 별 이벤트 (BadEvent / GoodEvent) 로 분리 + 순차 실행.
 *
 * <h3>예상 동작 — 근거는 "@Async 스레드 = 트랜잭션/영속성 컨텍스트 없음"</h3>
 * <ol>
 *   <li>BadListener — @Transactional 없음. Spring Data JPA 의 findById 가 자체 readOnly 트랜잭션을 열고 닫음 →
 *       그 직후 getComments() 는 트랜잭션 밖 + @Async 별 스레드 → 영속성 컨텍스트 없음 → 폭발 기대.
 *       <b>단 예외 타입은 버전에 따라 달라질 수 있으므로 catch (Exception) 으로 넓혀서 어떤 예외든 콘솔에 찍히게 함.</b></li>
 *   <li>GoodListener — @Transactional(REQUIRES_NEW) + fetch join → 새 트랜잭션 + 새 영속성 컨텍스트 안에서 안전</li>
 * </ol>
 *
 * <h3>OSIV 와의 관계</h3>
 * OSIV 의 Lazy 보호는 <b>서블릿 요청 컨텍스트 안에서만</b> 동작 (DispatcherServlet → OSIV 인터셉터).
 * 이 데모는 main 에서 직접 호출 + @Async 별 스레드 → OSIV 와 무관하게 영속성 컨텍스트 없음.
 */
@SpringBootApplication(scanBasePackages = {"stage.s3", "domain", "infra"})
@EnableAsync
public class Stage3_3_AsyncContextLoss {

    public record BadEvent(Long postId) {}
    public record GoodEvent(Long postId) {}

    @Service
    public static class PostService {
        private final ApplicationEventPublisher publisher;
        public PostService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

        @Transactional
        public void publishBad(Long postId) {
            publisher.publishEvent(new BadEvent(postId));
        }

        @Transactional
        public void publishGood(Long postId) {
            publisher.publishEvent(new GoodEvent(postId));
        }
    }

    @Component
    public static class BadListener {
        private final PostRepository postRepo;
        public BadListener(PostRepository postRepo) { this.postRepo = postRepo; }

        @Async
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        // ★ @Transactional 없음. findById 는 Spring Data JPA 의 자체 readOnly 트랜잭션에서만 영속.
        // 그 직후 getComments() 는 트랜잭션 밖 + @Async 별 스레드 → 영속성 컨텍스트 없음 → 폭발 기대.
        public void on(BadEvent e) {
            System.out.println("  [Bad] thread=" + MeasurementLog.thread());
            try {
                Post p = postRepo.findById(e.postId()).orElseThrow();
                // findById 가 반환되는 순간 Spring Data JPA 가 만든 readOnly 트랜잭션은 이미 close
                int count = p.getComments().size();
                System.out.println("  [Bad] 댓글 수 = " + count + " (환경에 따라 결과 다를 수 있음)");
            } catch (Exception ex) {
                // 예외 타입은 버전에 따라 LazyInitializationException 또는 다른 형태
                System.out.println("  [Bad] 💥 " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }

    @Component
    public static class GoodListener {
        private final PostRepository postRepo;
        public GoodListener(PostRepository postRepo) { this.postRepo = postRepo; }

        @Async
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        @Transactional(propagation = Propagation.REQUIRES_NEW)   // ★ 새 트랜잭션 + 새 영속성 컨텍스트
        public void on(GoodEvent e) {
            System.out.println("  [Good] thread=" + MeasurementLog.thread());
            // fetch join 으로 Lazy 미리 가져옴
            Post p = postRepo.findAllWithCommentsJoinFetch().stream()
                .filter(x -> x.getId().equals(e.postId()))
                .findFirst()
                .orElseThrow();
            int count = p.getComments().size();
            System.out.println("  [Good] 댓글 수 = " + count + " ✓ 안전");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_3_AsyncContextLoss.class, args);
        ctx.getBean(SchemaSeeder.class).seed(3, 2);

        MeasurementLog.title("STAGE 3-3 — @Async + AFTER_COMMIT 새 스레드 함정 (6 주차 회수)");

        MeasurementLog.section("(1) BadEvent — @Transactional 없이 Lazy 접근 → LazyInitializationException");
        ctx.getBean(PostService.class).publishBad(1L);
        Thread.sleep(300);

        MeasurementLog.section("(2) GoodEvent — REQUIRES_NEW + fetch join → 안전");
        ctx.getBean(PostService.class).publishGood(1L);
        Thread.sleep(300);

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · @Async + AFTER_COMMIT = 새 스레드 + 본 트랜잭션 끝 = 영속성 컨텍스트 X");
        System.out.println("  · findById 는 Spring Data JPA 의 자체 readOnly 트랜잭션 안에서만 영속");
        System.out.println("    → 호출이 끝나는 순간 detach → Lazy 접근 시 영속성 컨텍스트 없음 → 폭발");
        System.out.println("  · 예외 타입은 버전에 따라 LazyInitializationException 또는 다른 형태 — 콘솔 확인");
        System.out.println("  · OSIV ON 이어도 무관 — OSIV 는 서블릿 요청 컨텍스트 안에서만 동작 (main 직접 호출은 무관)");
        System.out.println("  · 해결: @Transactional(REQUIRES_NEW) + fetch join");
        System.out.println("  · 더 권장: 이벤트 payload 에 필요한 데이터 미리 포함 (DTO record) → DB 안 봐도 됨");
        ctx.close();
    }
}
