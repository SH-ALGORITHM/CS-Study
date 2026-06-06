package stage.s3;

import infra.MeasurementLog;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * STAGE 3-1 — Connection Pool 고갈 시뮬레이션 (Stage2_3 의 짝).
 *
 * <h3>시나리오</h3>
 * Pool maxTotal=2 + 동시 10 요청 + 외부 SlowApi 1 초 지연.
 * 처음 2 개만 연결 획득 → 나머지 8 개는 풀에서 연결 대기.
 *
 * <h3>측정 공식</h3>
 * <pre>
 *   라운드 = ⌈요청 / maxTotal⌉ = ⌈10 / 2⌉ = 5
 *   총 시간 ≈ 5 × 외부 응답 1 초 ≈ 5 초
 * </pre>
 *
 * <h3>Stage2_3 와의 차이</h3>
 * <ul>
 *   <li>Stage2_3 — 호출 측 <b>스레드</b> 풀 (ExecutorService 10)</li>
 *   <li>Stage3_1 — HTTP 클라이언트 <b>커넥션</b> 풀 (maxTotal 2)</li>
 *   <li>둘 다 "외부 응답 대기로 풀 자원 점유" 라는 같은 메커니즘</li>
 * </ul>
 *
 * <h3>Pool + Timeout 혼용 함정 회피</h3>
 * Apache HttpClient 5 에서 Timeout 은 SimpleClientHttpRequestFactory 의 setter 가 아니라
 * <b>ConnectionConfig</b> 단에서. PoolingHttpClientConnectionManager 에 주입.
 */
@SpringBootApplication
public class Stage3_1_PoolExhaustion {

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Stage3_1_PoolExhaustion.class)
            .web(WebApplicationType.NONE).run(args);

        MeasurementLog.title("STAGE 3-1 — Connection Pool 고갈 시뮬레이션");

        // ★ Pool maxTotal=2 — Apache HttpClient 의 커넥션 풀
        PoolingHttpClientConnectionManager pool = new PoolingHttpClientConnectionManager();
        pool.setMaxTotal(2);
        pool.setDefaultMaxPerRoute(2);

        // ★ Timeout 은 ConnectionConfig 단에서 (혼용 함정 회피)
        pool.setDefaultConnectionConfig(ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(1))
            .setSocketTimeout(Timeout.ofSeconds(5))         // read timeout
            .build());

        HttpClient httpClient = HttpClients.custom()
            .setConnectionManager(pool)
            .build();

        RestClient client = RestClient.builder()
            .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
            .baseUrl("http://localhost:8081")
            .build();

        int requestCount = 10;
        ExecutorService workers = Executors.newFixedThreadPool(requestCount);   // 스레드는 충분
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(requestCount);
        CountDownLatch go = new CountDownLatch(1);

        for (int i = 0; i < requestCount; i++) {
            workers.submit(() -> {
                try {
                    go.await();
                    client.get().uri("/slow?ms=1000").retrieve().body(String.class);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        long t1 = System.nanoTime();
        go.countDown();
        done.await(60, TimeUnit.SECONDS);
        long totalMs = (System.nanoTime() - t1) / 1_000_000;

        workers.shutdown();
        workers.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("[측정] Pool maxTotal=2 / 요청=" + requestCount + " / 외부 1 초");
        System.out.println("  성공 = " + success.get() + "  /  실패 = " + failure.get());
        System.out.println("  총 시간 = " + totalMs + "ms");
        System.out.println();
        System.out.println("[학습] 공식: 총 시간 ≈ ⌈요청/maxTotal⌉ × 외부 응답");
        System.out.println("       이 데모: 5 라운드 × 1 초 ≈ 5 초");
        System.out.println("       Pool maxTotal=10 으로 늘리면 1 라운드 × 1 초 ≈ 1 초");
        System.out.println("[정확] Stage2_3 = 스레드 풀 고갈 / Stage3_1 = 커넥션 풀 고갈 — 같은 메커니즘");
        ctx.close();
    }
}
