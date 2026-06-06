package server;

import infra.MeasurementLog;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가짜 외부 API 서버 — 의도적 지연. 클라이언트 stage 들이 이 서버를 호출.
 *
 * <h3>실행</h3>
 * <pre>./gradlew run -PmainClass=server.SlowApiServer</pre>
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>GET /slow?ms=5000 — 지정한 ms 동안 sleep 후 응답</li>
 *   <li>GET /fast — 즉시 응답</li>
 *   <li>GET /flaky — 70% 확률로 500 에러 (CB 데모 안정성)</li>
 *   <li>GET /stats — 처리 횟수</li>
 * </ul>
 *
 * <h3>포트</h3>
 * 8081 (클라이언트 stage 들은 다른 포트)
 */
@SpringBootApplication
@RestController
public class SlowApiServer {

    private final AtomicInteger totalCalls = new AtomicInteger(0);

    @Bean
    public org.springframework.boot.web.servlet.server.ServletWebServerFactory webServer() {
        org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory f =
            new org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory();
        f.setPort(8081);
        return f;
    }

    @GetMapping("/slow")
    public String slow(@RequestParam(defaultValue = "5000") long ms) throws InterruptedException {
        int n = totalCalls.incrementAndGet();
        System.out.println("[Server #" + n + "] sleep " + ms + "ms");
        Thread.sleep(ms);
        return "ok #" + n;
    }

    @GetMapping("/fast")
    public String fast() {
        int n = totalCalls.incrementAndGet();
        return "ok #" + n;
    }

    @GetMapping("/flaky")
    public String flaky() throws InterruptedException {
        int n = totalCalls.incrementAndGet();
        Thread.sleep(100);
        // CB 데모 안정성 — 실패율 70% (50% 임계치 + 20% 여유로 OPEN 안정 재현)
        if (Math.random() < 0.7) {
            throw new RuntimeException("flaky failure");
        }
        return "ok #" + n;
    }

    @GetMapping("/stats")
    public int stats() { return totalCalls.get(); }

    public static void main(String[] args) {
        SpringApplication.run(SlowApiServer.class, args);
        MeasurementLog.title("SlowApiServer started on :8081");
        System.out.println("  GET /slow?ms=5000  — 5 초 지연");
        System.out.println("  GET /fast          — 즉시");
        System.out.println("  GET /flaky         — 70% 500 (CB 데모용)");
        System.out.println("  GET /stats         — 총 호출 수");
    }
}
