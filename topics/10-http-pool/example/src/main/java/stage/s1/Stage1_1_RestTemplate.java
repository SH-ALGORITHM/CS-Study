package stage.s1;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;

/**
 * STAGE 1-1 — RestTemplate (옛 표준, 참고용).
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>기본 SimpleClientHttpRequestFactory → HttpURLConnection 사용</li>
 *   <li>HttpURLConnection 도 JVM 차원에서 `http.keepAlive=true` (기본) 로 연결 캐시 →
 *       "매번 새 handshake" 는 부정확. 진짜 한계는 <b>풀 크기 / route 별 한도 / eviction 같은 제어 없음 + 동시성 환경에서 JVM 기본 캐시는 제한적</b></li>
 *   <li>Pool 효과 차이는 RTT 큰 실 외부 호출 + 동시성에서 드러남. 로컬은 차이 미미</li>
 *   <li>Spring 5+ 유지보수 모드. 새 프로젝트는 RestClient (Stage1_2)</li>
 * </ul>
 *
 * <h3>전제</h3>
 * SlowApiServer 가 :8081 에 떠 있어야. 다른 터미널에서:
 * <pre>./gradlew run -PmainClass=server.SlowApiServer</pre>
 */
@SpringBootApplication
public class Stage1_1_RestTemplate {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Stage1_1_RestTemplate.class).web(WebApplicationType.NONE).run(args);

        MeasurementLog.title("STAGE 1-1 — RestTemplate (Pool 없음)");

        RestTemplate rt = new RestTemplate();
        // 매 호출마다 새 TCP 연결 (Keep-Alive 도 SimpleClientHttpRequestFactory 한정)

        int n = 10;
        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            String response = rt.getForObject("http://localhost:8081/fast", String.class);
            if (i == 0 || i == n - 1) {
                System.out.println("  [" + (i + 1) + "] " + response);
            }
        }
        long totalMs = (System.nanoTime() - t1) / 1_000_000;

        System.out.println();
        System.out.println("[측정] " + n + " 회 호출 = " + totalMs + "ms (평균 " + totalMs / n + "ms)");
        System.out.println("[학습] 로컬이라 RTT 작아서 차이 미미. 실 외부 호출 (RTT 30ms+) 에서 Pool 효과 큼");
        ctx.close();
    }
}
