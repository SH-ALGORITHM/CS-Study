package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

// ─────────────────────────────────────────────────────────────
// STAGE 3-3 — self-invocation 함정 (5주차 @Transactional 함정 회수)
//
// @Async 도 프록시 메커니즘. 같은 클래스 안에서 this.asyncMethod() 호출하면
// 프록시를 우회 → 비동기 무시(동기 실행). 5주차 @Transactional self-invocation 과 동일.
// ─────────────────────────────────────────────────────────────

@Service
class SelfInvokeService {

    @Async
    public void asyncNotify(Long id) {
        System.out.println("  [asyncNotify] thread=" + Thread.currentThread().getName());
    }

    public void place(Long id) {
        System.out.println("[place] thread=" + Thread.currentThread().getName());
        // self-invocation: this 로 호출 → 프록시 우회
        asyncNotify(id);
    }
}

@SpringBootApplication
@EnableAsync
public class Stage3_3_SelfInvocation {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage3_3_SelfInvocation.class, args);
        ctx.getBean(SelfInvokeService.class).place(1L);

        try { Thread.sleep(300); } catch (InterruptedException ignored) {}

        MeasurementLog.save("s3-3", "self-invocation — this.asyncMethod() 는 @Async 무시(동기)");
    }
}
