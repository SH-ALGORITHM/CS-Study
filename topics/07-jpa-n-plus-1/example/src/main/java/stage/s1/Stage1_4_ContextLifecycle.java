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
 * STAGE 1-4 — 영속성 컨텍스트 수명 = 트랜잭션 수명.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>트랜잭션 안 setName() → UPDATE 자동 (변경 감지)</li>
 *   <li>트랜잭션 밖 (호출자) setName() → DB 반영 X (준영속 상태)</li>
 *   <li>준영속 Entity 는 그냥 자바 객체. 다시 영속화하려면 em.merge()</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s1", "domain", "infra"})
public class Stage1_4_ContextLifecycle {

    @Service
    public static class LifecycleDemo {
        private final AuthorRepository repo;
        public LifecycleDemo(AuthorRepository repo) { this.repo = repo; }

        @Transactional
        public Author fetchAndReturn(Long id) {
            // 트랜잭션 안 — 영속 상태
            return repo.findById(id).orElseThrow();
            // 메서드 종료 → 트랜잭션 commit → 영속성 컨텍스트 close
        }

        @Transactional
        public Long seed() {
            return repo.save(new Author("원래 이름")).getId();
        }

        @Transactional(readOnly = true)
        public Author refetch(Long id) {
            return repo.findById(id).orElseThrow();
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1_4_ContextLifecycle.class, args);
        LifecycleDemo demo = ctx.getBean(LifecycleDemo.class);

        MeasurementLog.title("STAGE 1-4 — 영속성 컨텍스트 수명 = 트랜잭션 수명");

        Long id = demo.seed();
        MeasurementLog.marker("[Setup] author saved id=" + id);

        MeasurementLog.section("(1) 트랜잭션 밖에서 setName() → DB 반영 X");
        Author detached = demo.fetchAndReturn(id);
        System.out.println("  현재 트랜잭션 밖 — author 는 준영속 상태");
        detached.setName("호출자에서 setName");
        MeasurementLog.marker("[A] setName() 끝 — UPDATE SQL 없음");

        MeasurementLog.section("(2) DB 재조회로 확인");
        Author after = demo.refetch(id);
        System.out.println("  DB 의 name = " + after.getName() + " (예상: '원래 이름')");

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · 트랜잭션 밖 setName() = 그냥 자바 객체 setter. UPDATE 안 나감");
        System.out.println("  · 다시 영속화하려면 em.merge() 또는 새 트랜잭션 안에서 findById + setter");
        System.out.println("  · 트랜잭션 밖에서 Lazy 컬렉션 접근 시 LazyInitializationException (STAGE 3-1)");
        ctx.close();
    }
}
