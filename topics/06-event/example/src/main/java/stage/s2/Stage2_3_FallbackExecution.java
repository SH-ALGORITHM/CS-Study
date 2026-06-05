package stage.s2;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * STAGE 2-3 — fallbackExecution — 트랜잭션 밖에서 publishEvent 시 동작.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>NoFallback — 트랜잭션 없으면 조용히 무시 (WARN 로그조차 X) ← 함정</li>
 *   <li>WithFallback — 트랜잭션 없어도 즉시 실행</li>
 *   <li>실무 — 로그인 / 인증처럼 트랜잭션 없는 메서드에서 이벤트 발행 시 fallback=true</li>
 * </ul>
 */
@Configuration
@EnableAutoConfiguration
public class Stage2_3_FallbackExecution {

    public record LoggedInEvent(Long userId) {}

    @Bean
    public LoginService loginService(ApplicationEventPublisher publisher) {
        return new LoginService(publisher);
    }

    @Bean public NoFallbackListener noFallback() { return new NoFallbackListener(); }
    @Bean public WithFallbackListener withFallback() { return new WithFallbackListener(); }

    public static class LoginService {
        private final ApplicationEventPublisher publisher;
        public LoginService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

        // ★ @Transactional 없음 — 인증 자체는 트랜잭션 없는 경우
        public void login(Long userId) {
            System.out.println("  [SERVICE] login (트랜잭션 없음) userId=" + userId);
            publisher.publishEvent(new LoggedInEvent(userId));
            System.out.println("  [SERVICE] return");
        }
    }

    public static class NoFallbackListener {
        // 기본 fallbackExecution = false
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(LoggedInEvent e) {
            System.out.println("    [NoFallback] userId=" + e.userId() + " ← 호출됨!! (트랜잭션 안이라면)");
        }
    }

    public static class WithFallbackListener {
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
                                    fallbackExecution = true)
        public void on(LoggedInEvent e) {
            System.out.println("    [WithFallback] userId=" + e.userId() + " ← 트랜잭션 없어도 호출됨");
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_3_FallbackExecution.class, args);

        MeasurementLog.title("STAGE 2-3 — fallbackExecution (트랜잭션 밖 publishEvent)");

        ctx.getBean(LoginService.class).login(42L);

        System.out.println();
        System.out.println("[학습 포인트]");
        System.out.println("  · NoFallback (기본) — 호출 안 됨. WARN 도 안 나옴 → 함정");
        System.out.println("  · WithFallback (true) — 즉시 실행. 그냥 @EventListener 처럼");
        System.out.println("  · 트랜잭션 안 / 밖 모두 발행되는 메서드는 fallback=true 필수");
        ctx.close();
    }
}
