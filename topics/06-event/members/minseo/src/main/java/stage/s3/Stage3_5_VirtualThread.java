package stage.s3;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * STAGE 3-5 — Java 21 Virtual Thread + Spring Boot 3.2 (재고 도메인)
 * 
 * [시나리오]
 * 대량의 재고 연동 작업(I/O Bound)이 발생했을 때 가상 스레드의 효율성을 확인합니다.
 * application.yml에 spring.threads.virtual.enabled=true 설정이 필요합니다.
 */
@Configuration
@EnableAutoConfiguration
@EnableAsync
public class Stage3_5_VirtualThread {

    public record InventorySyncEvent(Long id) {}

    @Bean
    public InventoryService inventoryService(ApplicationEventPublisher publisher) {
        return new InventoryService(publisher);
    }

    @Bean public IoBoundListener l1() { return new IoBoundListener("L1"); }
    @Bean public IoBoundListener l2() { return new IoBoundListener("L2"); }
    @Bean public IoBoundListener l3() { return new IoBoundListener("L3"); }

    public static class InventoryService {
        private final ApplicationEventPublisher publisher;
        public InventoryService(ApplicationEventPublisher publisher) { this.publisher = publisher; }

        public void syncAll(Long id) {
            System.out.println("[Service] 재고 동기화 시작 " + MeasurementLog.thread());
            publisher.publishEvent(new InventorySyncEvent(id));
            System.out.println("[Service] 이벤트 발행 완료");
        }
    }

    public static class IoBoundListener {
        private final String name;
        public IoBoundListener(String name) { this.name = name; }

        @Async
        @EventListener
        public void on(InventorySyncEvent e) throws InterruptedException {
            Thread current = Thread.currentThread();
            // ★ 핵심: isVirtual()을 통해 가상 스레드 여부 확인
            System.out.println("    [" + name + "] 처리 시작 | Thread: " + current.getName() 
                + " | isVirtual: " + current.isVirtual());
            
            Thread.sleep(200); // I/O 블로킹 시뮬레이션
            System.out.println("    [" + name + "] 처리 완료");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage3_5_VirtualThread.class, args);
        InventoryService service = ctx.getBean(InventoryService.class);

        MeasurementLog.title("STAGE 3-5 — Virtual Thread 활용 (재고 도메인)");

        service.syncAll(1L);

        // 비동기 작업 완료 대기
        Thread.sleep(500);

        System.out.println("\n[학습 포인트]");
        System.out.println("  1. application.yml의 spring.threads.virtual.enabled=true 설정으로 가상 스레드 활성화");
        System.out.println("  2. 리스너 로그에서 isVirtual: true가 나오는지 확인");
        System.out.println("  3. 가상 스레드는 수천 개를 생성해도 오버헤드가 적어 I/O Bound 작업에 최적입니다.");
        System.out.println("  4. ThreadPoolTaskExecutor 빈을 직접 등록하면 가상 스레드 자동 설정이 무효화되니 주의하세요.");

        ctx.close();
    }
}
