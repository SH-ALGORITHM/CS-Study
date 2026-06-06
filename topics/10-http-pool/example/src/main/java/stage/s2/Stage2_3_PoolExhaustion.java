package stage.s2;

import infra.MeasurementLog;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * STAGE 2-3 — 스레드 풀 고갈 시뮬레이션 (★ 핵심).
 *
 * <h3>정확한 시뮬레이션 범위</h3>
 * 이 데모는 톰캣 워커가 아닌 <b>클라이언트 측 ExecutorService(10) 의 풀 고갈</b>을 보여줌
 * (내 서버가 외부를 호출하는 쪽). 진짜 톰캣 워커 고갈은 톰캣을 띄우고
 * server.tomcat.threads.max=10 + 컨트롤러가 외부 호출 + 외부에서 ab 로 동시 부하 — 복잡.
 *
 * <b>하지만 메커니즘은 정확히 같음</b> — 어느 풀이든 스레드가 외부 응답 대기로 점유되면
 * 그만큼 다른 작업 처리 불가. 시나리오의 톰캣 워커 서사가 그대로 적용.
 *
 * <h3>측정 공식 (worker 10 / 요청 50 / 외부 5 초)</h3>
 * <pre>
 *   요청 / worker = 라운드 = 5
 *   총 시간 ≈ 라운드 × 라운드당 시간
 *     · Timeout 미설정 → 라운드당 = 외부 응답 5 초 → 총 ~ 25 초
 *     · Timeout 3 초    → 라운드당 = 3 초            → 총 ~ 15 초
 *   본인 환경에서 직접 확인.
 * </pre>
 */
@SpringBootApplication
public class Stage2_3_PoolExhaustion {

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Stage2_3_PoolExhaustion.class).web(WebApplicationType.NONE).run(args);

        MeasurementLog.title("STAGE 2-3 — 스레드 풀 고갈 시뮬레이션");

        // 클라이언트 (timeout 3 초)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(3000);
        RestClient client = RestClient.builder()
            .requestFactory(factory)
            .baseUrl("http://localhost:8081")
            .build();

        // 톰캣 워커 시뮬레이션 = 10 스레드
        int workerCount = 10;
        int requestCount = 50;
        ExecutorService workers = Executors.newFixedThreadPool(workerCount);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(requestCount);
        CountDownLatch go = new CountDownLatch(1);

        for (int i = 0; i < requestCount; i++) {
            workers.submit(() -> {
                try {
                    go.await();
                    client.get().uri("/slow?ms=5000").retrieve().body(String.class);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        long t1 = System.nanoTime();
        go.countDown();      // 동시 출발
        done.await(60, TimeUnit.SECONDS);
        long totalMs = (System.nanoTime() - t1) / 1_000_000;

        workers.shutdown();
        workers.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("[측정] worker=" + workerCount + " / 요청=" + requestCount
            + " (외부 5 초 지연 / read=3s)");
        System.out.println("  성공 = " + success.get() + "  /  실패 = " + failure.get());
        System.out.println("  총 시간 = " + totalMs + "ms");
        System.out.println();
        System.out.println("[학습] 공식: 총 시간 ≈ ⌈요청/worker⌉ × 라운드당 시간");
        System.out.println("       라운드당 시간 = timeout 미설정이면 외부 응답 시간 / 설정이면 timeout");
        System.out.println("       이 데모는 worker 10 / 요청 50 / 외부 5s / read 3s → 5 라운드 × 3 초 ≈ 15 초");
        System.out.println("       (timeout 미설정이면 5 라운드 × 5 초 = 25 초)");
        System.out.println("[정확] 클라이언트 풀 시뮬레이션 — 톰캣 워커 풀도 같은 메커니즘이지만 진짜 톰캣 시연은 별도 구조");
        ctx.close();
    }
}
