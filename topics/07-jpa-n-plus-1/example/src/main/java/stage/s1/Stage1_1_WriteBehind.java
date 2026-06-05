package stage.s1;

import domain.Author;
import domain.AuthorRepository;
import domain.Post;
import domain.PostRepository;
import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 1-1 — 쓰기 지연 (write-behind).
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>save() 호출 직후엔 SQL 로그에 INSERT 가 안 나옴 (마커 [A] [B] 사이)</li>
 *   <li>메서드 종료 = commit 직전 flush 시점에 INSERT 일괄 발행</li>
 *   <li>단 ID 자동 생성 전략이 IDENTITY 면 ID 받기 위해 persist 즉시 INSERT (예외)</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s1", "domain", "infra"})
public class Stage1_1_WriteBehind {

    @Service
    public static class WriteBehindDemo {
        private final AuthorRepository authorRepo;
        private final PostRepository postRepo;

        public WriteBehindDemo(AuthorRepository authorRepo, PostRepository postRepo) {
            this.authorRepo = authorRepo;
            this.postRepo = postRepo;
        }

        @Transactional
        public void run() {
            Author author = authorRepo.save(new Author("작성자"));
            MeasurementLog.marker("[A] author save() 끝");

            Post p1 = postRepo.save(new Post("첫 글", author));
            MeasurementLog.marker("[B] post save() 첫 번째 끝");

            Post p2 = postRepo.save(new Post("두 번째 글", author));
            MeasurementLog.marker("[C] post save() 두 번째 끝");

            MeasurementLog.marker("[D] 메서드 종료 직전 — commit 시점에 flush");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1_1_WriteBehind.class, args);

        MeasurementLog.title("STAGE 1-1 — 쓰기 지연 (write-behind)");
        ctx.getBean(WriteBehindDemo.class).run();

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · SQL 로그와 [A] [B] [C] [D] 마커 순서 비교");
        System.out.println("  · ID 자동 생성이 IDENTITY 라 INSERT 는 save 시점에 발행 (ID 필요)");
        System.out.println("  · SEQUENCE / TABLE 전략이면 진짜 commit 직전까지 INSERT 지연");
        ctx.close();
    }
}
