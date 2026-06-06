package stage.s2;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestClient;

/**
 * STAGE 2-1 — Timeout 없으면 무한 대기. SlowApi 5 초 지연 → 클라이언트 5 초 점유.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>RestClient 기본 — connect / read timeout 미설정 → 자동 선택된 factory 의 기본 (대개 무한 또는 매우 김)</li>
 *   <li>외부 서버가 응답 안 보내면 영원히 대기 = 톰캣 워커 영구 점유</li>
 *   <li>외부 서버 다운 = 내 서버 다운 (실무 장애 1 위)</li>
 * </ul>
 *
 * <h3>이 데모는 5 초만 시뮬레이션</h3>
 * SlowApiServer 의 /slow?ms=5000 호출 → 5 초 대기 후 응답.
 * 진짜 무한 대기 시연은 SlowApiServer 를 멈추거나 잘못된 호스트로.
 */
@SpringBootApplication
public class Stage2_1_TimeoutMissing {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Stage2_1_TimeoutMissing.class).web(WebApplicationType.NONE).run(args);

        MeasurementLog.title("STAGE 2-1 — Timeout 없음 → 5 초 대기");

        RestClient client = RestClient.builder()
            .baseUrl("http://localhost:8081")
            .build();
        // ⚠️ timeout 미설정

        long t1 = System.nanoTime();
        try {
            String response = client.get().uri("/slow?ms=5000").retrieve().body(String.class);
            long ms = (System.nanoTime() - t1) / 1_000_000;
            System.out.println("  응답: " + response + "  (" + ms + "ms)");
        } catch (Exception e) {
            long ms = (System.nanoTime() - t1) / 1_000_000;
            System.out.println("  예외 (" + ms + "ms): " + e.getClass().getSimpleName());
        }

        System.out.println();
        System.out.println("[학습] timeout 미설정 = 외부 응답 도착할 때까지 영구 대기");
        System.out.println("       서버 다운 / 무한 지연 시 톰캣 워커 영원히 점유 → 다른 요청 거부");
        System.out.println("       해결 → Stage2_2 (Timeout 명시)");
        ctx.close();
    }
}
