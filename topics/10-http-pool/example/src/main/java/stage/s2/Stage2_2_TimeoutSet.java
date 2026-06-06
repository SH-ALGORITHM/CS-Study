package stage.s2;

import infra.MeasurementLog;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * STAGE 2-2 — Timeout 명시.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li><b>connect timeout 1s</b> — 외부 서버 다운 / 방화벽 / DNS 실패 시 발동.
 *       이 데모는 localhost 라 즉시 연결 → connect 는 실제로 발동 안 함. 진짜 시연은 라우팅 안 되는 IP 로</li>
 *   <li><b>read timeout 3s</b> — 응답 대기 한도. SlowApi 5 초 지연 → 3 초 후 예외</li>
 *   <li>예외 타입 — 보통 ResourceAccessException 로 래핑되나 Spring/JDK 버전에 따라 다를 수 있음.
 *       콘솔에서 직접 확인 (catch (Exception) 으로 넓게 잡음)</li>
 * </ul>
 */
@SpringBootApplication
public class Stage2_2_TimeoutSet {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Stage2_2_TimeoutSet.class).web(WebApplicationType.NONE).run(args);

        MeasurementLog.title("STAGE 2-2 — Timeout 명시 (connect=1s / read=3s)");

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(1).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(3).toMillis());

        RestClient client = RestClient.builder()
            .requestFactory(factory)
            .baseUrl("http://localhost:8081")
            .build();

        long t1 = System.nanoTime();
        try {
            String response = client.get().uri("/slow?ms=5000").retrieve().body(String.class);
            long ms = (System.nanoTime() - t1) / 1_000_000;
            System.out.println("  응답: " + response + "  (" + ms + "ms)");
        } catch (Exception e) {
            long ms = (System.nanoTime() - t1) / 1_000_000;
            System.out.println("  예외 (" + ms + "ms): " + e.getClass().getSimpleName()
                + " — 타입은 버전 따라 다를 수 있음. 보통 ResourceAccessException");
            System.out.println("  → read timeout 직후 빠른 실패. 호출 측 스레드 회수 ✓");
        }

        System.out.println();
        System.out.println("[학습] connect / read timeout 둘 다 명시. 외부 SLA 기준으로 결정");
        System.out.println("       PG 보통 5 초 / 알림 1 초 / OAuth 2 초 등");
        System.out.println("       (이 데모는 localhost 라 connect 는 발동 안 함. read 만 시연)");
        ctx.close();
    }
}
