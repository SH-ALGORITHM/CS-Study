package stage.S3;

import domain.AuthProvider;
import domain.AuthRepository;
import domain.AuthService;
import domain.GoogleAuthProvider;
import domain.JwtAuthProvider;
import domain.KakaoAuthProvider;
import domain.SessionAuthProvider;
import infra.MeasurementLog;

/**
 * STAGE 3-1 (A): 순수 main() 부팅 시간 — Spring 안 씀.
 *
 * JVM 웜업으로 인한 측정 오차를 막기 위해 A, B, C를 각각 따로 실행합니다.
 */
public class Stage3_A_Pure {

    public static void main(String[] args) {
        long t1 = System.nanoTime();

        // 스프링 도움 없이 개발자가 직접 조립 (의존성이 많을수록 코드가 길어짐)
        AuthProvider jwt = new JwtAuthProvider();
        AuthProvider kakao = new KakaoAuthProvider();
        AuthProvider google = new GoogleAuthProvider();
        AuthProvider session = new SessionAuthProvider();
        
        // AuthRepository는 DataSource가 필요하지만 시간 측정용이므로 null 전달
        AuthRepository repo = new AuthRepository(null); 
        
        AuthService service = new AuthService(jwt, repo);

        long elapsed = (System.nanoTime() - t1) / 1_000_000;

        System.out.println("\n=== 순수 main() 부팅 ===");
        System.out.println("부팅 시간: " + elapsed + "ms");
        System.out.println("생성한 객체: 4 provider + 1 repo + 1 service = 6 개");

        service.login("user1", "pass", "token");

        MeasurementLog.save("s3-1", "순수 main()", "부팅 시간 " + elapsed + "ms / 객체 6 개");
    }
}