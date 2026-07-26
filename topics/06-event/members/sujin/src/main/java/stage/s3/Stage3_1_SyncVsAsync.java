package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

// ─────────────────────────────────────────────────────────────
// STAGE 3-1/3-2 — 동기의 한계 → @Async
//
// Run A: 아래 리스너 그대로(동기) → publisher 총 시간 ≈ 500ms (리스너 시간이 그대로 더해짐)
// Run B: 리스너에 @Async 한 줄 추가 → publisher 즉시 반환(≈ 수 ms), 리스너는 task-N 스레드
// ─────────────────────────────────────────────────────────────

record PaymentNotifyEvent(Long paymentId) {}

@Service
class NotifyPublisher {
    private final ApplicationEventPublisher publisher;
    NotifyPublisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }

    void publish(Long id) {
        System.out.println("[publisher] publish — thread=" + Thread.currentThread().getName());
        publisher.publishEvent(new PaymentNotifyEvent(id));
        System.out.println("[publisher] return  — thread=" + Thread.currentThread().getName());
    }
}

@Component
class SlowNotifyListener {
    @Async
    @EventListener
    void on(PaymentNotifyEvent e) throws InterruptedException {
        System.out.println("  [slow] start — thread=" + Thread.currentThread().getName());
        Thread.sleep(500);                       // 외부 알림/이메일이 느리다고 가정
        System.out.println("  [slow] end");
    }
}

@SpringBootApplication
@EnableAsync
public class Stage3_1_SyncVsAsync {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage3_1_SyncVsAsync.class, args);
        var pub = ctx.getBean(NotifyPublisher.class);

        long t1 = System.nanoTime();
        pub.publish(1L);
        long ms = (System.nanoTime() - t1) / 1_000_000;
        System.out.println("publisher 총 시간: " + ms + "ms");

        // 비동기(Run B)면 리스너가 아직 안 끝났을 수 있어 JVM 종료 전 잠깐 대기
        try { Thread.sleep(800); } catch (InterruptedException ignored) {}

        MeasurementLog.save("s3-1", "publisher 블록 시간 = " + ms + "ms");    }
}
