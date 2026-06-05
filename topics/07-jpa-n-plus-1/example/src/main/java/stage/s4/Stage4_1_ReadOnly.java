package stage.s4;

import domain.Author;
import domain.AuthorRepository;
import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 4-1 — @Transactional(readOnly = true) 효과.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>readOnly=true — 변경 감지 X (스냅샷 안 만듦)</li>
 *   <li>setter 호출해도 UPDATE 안 나감 — 안전망</li>
 *   <li>일부 DB (MySQL) — START TRANSACTION READ ONLY 발행</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s4", "domain", "infra"})
public class Stage4_1_ReadOnly {

    @Service
    public static class ReadOnlyDemo {
        private final AuthorRepository repo;
        public ReadOnlyDemo(AuthorRepository repo) { this.repo = repo; }

        @Transactional
        public Long seed() {
            return repo.save(new Author("원래 이름")).getId();
        }

        @Transactional   // readOnly = false (기본)
        public void writeMode(Long id) {
            Author a = repo.findById(id).orElseThrow();
            a.setName("write 모드");
            MeasurementLog.marker("[write] setName 끝 — UPDATE 자동");
        }

        @Transactional(readOnly = true)
        public void readOnlyMode(Long id) {
            Author a = repo.findById(id).orElseThrow();
            a.setName("readOnly 모드");
            MeasurementLog.marker("[readOnly] setName 끝 — UPDATE 안 나감");
        }

        @Transactional(readOnly = true)
        public String currentName(Long id) {
            return repo.findById(id).orElseThrow().getName();
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_1_ReadOnly.class, args);
        ReadOnlyDemo demo = ctx.getBean(ReadOnlyDemo.class);

        MeasurementLog.title("STAGE 4-1 — @Transactional(readOnly = true)");

        Long id = demo.seed();

        MeasurementLog.section("(1) write 모드 — setName → UPDATE 자동");
        demo.writeMode(id);
        System.out.println("  DB 의 name = " + demo.currentName(id));

        MeasurementLog.section("(2) readOnly 모드 — setName → UPDATE 무시");
        demo.readOnlyMode(id);
        System.out.println("  DB 의 name = " + demo.currentName(id) + " (변경 안 됨)");

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · readOnly=true → 스냅샷 X / 변경 감지 X / flush 모드 MANUAL");
        System.out.println("  · 조회 전용 메서드에 명시 — 메모리 절약 + 실수 안전망");
        System.out.println("  · 일부 DB 는 트랜잭션 자체를 readonly 모드로 시작");
        ctx.close();
    }
}
