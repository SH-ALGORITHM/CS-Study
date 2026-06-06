package stage.s1;

import infra.MeasurementLog;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * STAGE 1-3 — Apache HttpClient 5 + PoolingConnectionManager 명시.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>maxTotal / maxPerRoute 직접 설정 (기본 2/route 너무 작음)</li>
 *   <li>Keep-Alive 명시</li>
 *   <li>RestClient.requestFactory 로 통합</li>
 * </ul>
 */
@SpringBootApplication
public class Stage1_3_ApacheHttpClient {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Stage1_3_ApacheHttpClient.class).web(WebApplicationType.NONE).run(args);

        MeasurementLog.title("STAGE 1-3 — Apache HttpClient 5 + Pool");

        PoolingHttpClientConnectionManager pool = new PoolingHttpClientConnectionManager();
        pool.setMaxTotal(100);
        pool.setDefaultMaxPerRoute(20);

        HttpClient httpClient = HttpClients.custom()
            .setConnectionManager(pool)
            .evictExpiredConnections()
            .build();

        RestClient client = RestClient.builder()
            .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
            .baseUrl("http://localhost:8081")
            .build();

        int n = 10;
        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            String response = client.get().uri("/fast").retrieve().body(String.class);
            if (i == 0 || i == n - 1) {
                System.out.println("  [" + (i + 1) + "] " + response);
            }
        }
        long totalMs = (System.nanoTime() - t1) / 1_000_000;

        System.out.println();
        System.out.println("[측정] " + n + " 회 호출 = " + totalMs + "ms");
        System.out.println("[학습] maxTotal=100 / maxPerRoute=20 명시. 기본 2/route 보다 실무에 적합");
        ctx.close();
    }
}
