package app;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 부하 생성기 — AppServer 에 지속적으로 요청 보내 메트릭 흐름 만듦.
 *
 * <h3>실행</h3>
 * <pre>./gradlew run -PmainClass=app.LoadGenerator</pre>
 *
 * <h3>관찰 — Prometheus 에서</h3>
 * <ul>
 *   <li><b>서버 실패율 5%</b> — Prometheus `rate(orders_total{result="failure"}[1m]) /
 *       rate(orders_total[1m])` 로 확인. 콘솔의 errors 가 아님</li>
 *   <li>콘솔 total = 응답 받은 요청 (200 + 500 둘 다)</li>
 *   <li>콘솔 errors = <b>클라 네트워크 예외만</b> (서버 500 은 정상 응답이라 errors X)</li>
 *   <li>"5% 는 메트릭으로 봐라" 가 12 주차 본 메시지</li>
 * </ul>
 */
public class LoadGenerator {

    public static void main(String[] args) throws InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        AtomicInteger total = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        System.out.println("=== LoadGenerator started — 초당 ~20 RPS, 5% 실패율 ===");
        System.out.println("    AppServer 가 :8080 에 떠 있어야 함");
        System.out.println("    Ctrl+C 로 종료");
        System.out.println();

        while (true) {
            for (int i = 0; i < 20; i++) {
                boolean fail = Math.random() < 0.05;
                String url = "http://localhost:8080/api/order?fail=" + fail;

                CompletableFuture.runAsync(() -> {
                    try {
                        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
                        client.send(req, HttpResponse.BodyHandlers.ofString());
                        total.incrementAndGet();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                });
            }
            Thread.sleep(1000);

            if (total.get() % 100 == 0 && total.get() > 0) {
                System.out.println("[LoadGen] total=" + total.get() + " errors=" + errors.get());
            }
        }
    }
}
