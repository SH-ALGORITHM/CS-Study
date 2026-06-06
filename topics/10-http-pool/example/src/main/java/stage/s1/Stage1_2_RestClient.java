package stage.s1;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestClient;

/**
 * STAGE 1-2 — RestClient (Spring 6.1+ 권장).
 *
 * <h3>관찰 포인트 — 기본 factory 는 클래스패스로 결정</h3>
 * RestClient.builder() 가 requestFactory 지정 안 하면 Spring 의 자동 탐지 순서:
 * <pre>
 *   Apache HttpComponents (httpclient5) → Jetty → SimpleClientHttpRequestFactory (HttpURLConnection)
 * </pre>
 * JDK HttpClient (Java 11+) 는 자동 탐지 체인에 <b>없음</b> — `JdkClientHttpRequestFactory` 명시 필요.
 * <p>
 * 이 build.gradle 에 httpclient5 의존성 있어서 <b>실제로는 Apache HttpComponents 자동 선택</b>.
 * 즉 Stage1_3 의 명시 Pool 과 사실상 같은 백엔드. 정확한 factory 는 디버그 로그로 확인.
 *
 * <h3>그 외</h3>
 * <ul>
 *   <li>fluent API — 가독성 좋음</li>
 *   <li>RestTemplate 의 후속. WebClient (Reactor 학습 곡선) 없이 깔끔</li>
 * </ul>
 */
@SpringBootApplication
public class Stage1_2_RestClient {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Stage1_2_RestClient.class).web(WebApplicationType.NONE).run(args);

        MeasurementLog.title("STAGE 1-2 — RestClient (Spring 6.1+)");

        RestClient client = RestClient.builder()
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
        System.out.println("[측정] " + n + " 회 호출 = " + totalMs + "ms (평균 " + totalMs / n + "ms)");
        System.out.println("[학습] RestClient 의 기본 factory 는 클래스패스 의존 자동 선택:");
        System.out.println("       Apache HttpComponents → Jetty → Simple. JDK HttpClient 는 자동 X");
        System.out.println("       이 build.gradle 에 httpclient5 있어서 실제 백엔드 = Apache");
        ctx.close();
    }
}
