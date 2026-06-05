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
 * STAGE 1-3 — 변경 감지 (Dirty Checking). 가장 마법 같은 자리.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>setName() 만 했는데 UPDATE SQL 자동 발행</li>
 *   <li>save() / merge() 한 줄도 안 호출했는데 — 영속성 컨텍스트가 스냅샷과 비교</li>
 *   <li>flush 시점 (commit 직전) 에 UPDATE 발행</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s1", "domain", "infra"})
public class Stage1_3_DirtyChecking {

    @Service
    public static class DirtyCheckingDemo {
        private final AuthorRepository repo;
        public DirtyCheckingDemo(AuthorRepository repo) { this.repo = repo; }

        // seed 를 별도 트랜잭션으로 — INSERT 를 먼저 확정시켜야 새 트랜잭션의 UPDATE 가 단독으로 발행됨.
        // 한 트랜잭션 안에서 save 직후 setName 하면 IDENTITY INSERT 시점에 이미 새 값으로 박혀 UPDATE 안 나갈 수 있음.
        @Transactional
        public Long seed() {
            return repo.save(new Author("원래 이름")).getId();
        }

        @Transactional
        public void changeIt(Long id) {
            MeasurementLog.section("(1) findById → setName() 호출 (save/merge 없이)");
            Author author = repo.findById(id).orElseThrow();
            author.setName("바뀐 이름");
            MeasurementLog.marker("[A] setName() 끝 — 아직 UPDATE 안 나감");

            MeasurementLog.section("(2) 메서드 종료 → commit 시점에 UPDATE 단독 발행");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1_3_DirtyChecking.class, args);
        DirtyCheckingDemo svc = ctx.getBean(DirtyCheckingDemo.class);

        MeasurementLog.title("STAGE 1-3 — 변경 감지 (Dirty Checking)");

        Long id = svc.seed();
        MeasurementLog.marker("[Setup] author saved id=" + id + " (seed 트랜잭션 종료)");
        svc.changeIt(id);

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · save() / merge() 호출 없이도 UPDATE SQL 자동 발행");
        System.out.println("  · 메커니즘: 영속 시점 스냅샷 ↔ flush 시점 현재 값 비교");
        System.out.println("  · 비용: 모든 영속 Entity 의 스냅샷 보관 → 메모리. @Transactional(readOnly=true) 가 회피");
        ctx.close();
    }
}
