package stage.s3;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import domain.EmailSender;
import domain.NotificationLogRepository;
import domain.NotificationService;
import domain.PushSender;
import domain.SlackSender;
import domain.SmsSender;
import java.util.Arrays;
import javax.sql.DataSource;

/**
 * STAGE 3-1 A. 순수 자바 (Spring 사용 X).
 *
 * 객체 생성 + 의존성 연결을 본인 코드가 직접 수행. IoC 컨테이너 오버헤드 0.
 * Spring 의 컨테이너 부팅 비용 비교의 baseline 으로 사용.
 *
 * NOTE: NotificationService 는 @Qualifier("email") 로 sender 1개를 받지만,
 *   순수 자바에선 어떤 인스턴스를 줘도 됨 → EmailSender 인스턴스 전달.
 *   비교 fairness 를 위해 SmsSender/PushSender/SlackSender 도 같이 new 한다.
 *   ([NotificationService 가 받지 않더라도 컨테이너 등록 비용에 해당하는 객체 생성을 맞춤])
 */
public class Stage3_A_Pure {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5433/csstudy";
    private static final String USERNAME = "csstudy";
    private static final String PASSWORD = "csstudy1234";
    private static final int ITERATIONS = 5;

    public static void main(String[] args) {
        long[] elapsedNs = new long[ITERATIONS];
        for (int i = 0; i < ITERATIONS; i++) {
            elapsedNs[i] = measureOnce();
            System.out.printf("[%d] 순수 자바 직접 생성 = %.3f ms%n", i + 1, elapsedNs[i] / 1_000_000.0);
        }

        double avgWarmMs = warmAverageMs(elapsedNs);
        System.out.println();
        System.out.println("=== STAGE 3-1 A. 순수 자바 (Spring X) ===");
        System.out.println("측정 (ns)            = " + Arrays.toString(elapsedNs));
        System.out.println("측정 (ms)            = " + Arrays.toString(toMs(elapsedNs)));
        System.out.printf("워밍업 1회 제외 4회 평균 = %.3f ms%n", avgWarmMs);
        System.out.println();
        System.out.println("Spring 컨테이너 비용 0 — 객체를 본인이 직접 new + 의존성 직접 연결");
    }

    private static long measureOnce() {
        long start = System.nanoTime();

        DataSource ds = new HikariDataSource(hikariConfig());

        NotificationLogRepository repository = new NotificationLogRepository(ds);

        EmailSender email = new EmailSender();
        @SuppressWarnings("unused") SmsSender sms = new SmsSender();
        @SuppressWarnings("unused") PushSender push = new PushSender();
        @SuppressWarnings("unused") SlackSender slack = new SlackSender();

        NotificationService service = new NotificationService(email, repository);

        long elapsed = System.nanoTime() - start;

        // 측정 후 cleanup — 다음 측정에 영향 안 가도록
        ((HikariDataSource) ds).close();
        // service / sender 들은 GC 에 맡김
        @SuppressWarnings("unused") NotificationService held = service;

        return elapsed;
    }

    private static HikariConfig hikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(JDBC_URL);
        config.setUsername(USERNAME);
        config.setPassword(PASSWORD);
        config.setMaximumPoolSize(10);
        config.setPoolName("sujin-w04-stage3-pure");
        config.setInitializationFailTimeout(-1); // DB 없어도 부팅 측정 가능
        return config;
    }

    private static double warmAverageMs(long[] elapsedNs) {
        long sumNs = 0;
        for (int i = 1; i < elapsedNs.length; i++) {
            sumNs += elapsedNs[i];
        }
        return sumNs / (double) (elapsedNs.length - 1) / 1_000_000.0;
    }

    private static double[] toMs(long[] elapsedNs) {
        double[] ms = new double[elapsedNs.length];
        for (int i = 0; i < elapsedNs.length; i++) {
            ms[i] = elapsedNs[i] / 1_000_000.0;
        }
        return ms;
    }
}
