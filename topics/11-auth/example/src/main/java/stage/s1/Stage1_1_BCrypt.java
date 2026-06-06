package stage.s1;

import infra.MeasurementLog;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * STAGE 1-1 — BCrypt 동작 직접 관찰. Spring Boot 부팅 없이 라이브러리만.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>같은 평문 → <b>매번 다른 해시</b> (salt 자동)</li>
 *   <li>matches() 로 검증 — equals 비교 X</li>
 *   <li>cost factor — 기본 10 (2^10 = 1024 라운드). 조정 시 시간 비용 ↑</li>
 *   <li>해시 형식 — $2a$10$...salt...해시</li>
 * </ul>
 */
public class Stage1_1_BCrypt {

    public static void main(String[] args) {
        MeasurementLog.title("STAGE 1-1 — BCrypt 동작");

        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String plain = "password123";

        MeasurementLog.section("(1) 같은 평문 → 매번 다른 해시 (salt 자동)");
        String hash1 = encoder.encode(plain);
        String hash2 = encoder.encode(plain);
        System.out.println("  hash1 = " + hash1);
        System.out.println("  hash2 = " + hash2);
        System.out.println("  hash1.equals(hash2) ? " + hash1.equals(hash2));

        MeasurementLog.section("(2) matches() — 둘 다 같은 평문에 OK");
        System.out.println("  matches(plain, hash1) ? " + encoder.matches(plain, hash1));
        System.out.println("  matches(plain, hash2) ? " + encoder.matches(plain, hash2));
        System.out.println("  matches(wrong, hash1) ? " + encoder.matches("wrong", hash1));

        MeasurementLog.section("(3) cost factor 비교 — 기본 10 vs 14");
        PasswordEncoder strong = new BCryptPasswordEncoder(14);
        // 워밍업 — JIT / 클래스 로딩 편차 제거
        encoder.encode(plain);
        strong.encode(plain);

        long t1 = System.nanoTime();
        encoder.encode(plain);
        long ms10 = (System.nanoTime() - t1) / 1_000_000;
        long t2 = System.nanoTime();
        strong.encode(plain);
        long ms14 = (System.nanoTime() - t2) / 1_000_000;
        System.out.println("  cost=10 (기본): " + ms10 + "ms");
        System.out.println("  cost=14:       " + ms14 + "ms  (2^14 = 16K 라운드)");
        System.out.println("  → 이론상 16 배 — 단발 측정이라 환경 편차 큼. 여러 번 돌려 경향 확인");
        System.out.println("  → 비용 ↑ = brute force 비용 ↑. CPU 발전에 맞춰 cost 조정");

        System.out.println();
        System.out.println("[학습]");
        System.out.println("  · BCrypt 는 salt 자동 + cost factor → MD5/SHA 보다 안전");
        System.out.println("  · 해시 형식 $2a$10$...$ — algorithm + cost + salt + hash 다 들어감");
        System.out.println("  · DB 컬럼 VARCHAR(60) 이상 필요");
    }
}
