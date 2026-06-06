package stage.s2;

import infra.MeasurementLog;
import jwt.JwtService;

/**
 * STAGE 2-2 — JWT 함정 시연 ★ (가장 면접 직결).
 *
 * <h3>함정 4 가지</h3>
 * <ol>
 *   <li>alg=none — 라이브러리 차단 확인</li>
 *   <li>서명 검증 누락 — parseUnsecuredClaims 의 위험</li>
 *   <li>만료 (exp) — 짧은 만료 토큰 만들고 sleep 후 검증 시도</li>
 *   <li>다른 secret 으로 위조 시도 (Stage2_1 에서 시연)</li>
 * </ol>
 */
public class Stage2_2_JwtTraps {

    public static void main(String[] args) throws InterruptedException {
        MeasurementLog.title("STAGE 2-2 — JWT 함정 시연");

        String secret = "this-is-a-32-byte-secret-for-hs256!!";
        JwtService jwt = new JwtService(secret, 15);

        // ────────────────────────────────────────
        // (1) alg=none 공격 시뮬레이션
        // ────────────────────────────────────────
        MeasurementLog.section("(1) alg=none 공격 — jjwt 0.12+ 가 차단");

        // 헤더: {"alg":"none","typ":"JWT"} / payload: {"sub":"attacker","role":"ROLE_ADMIN"} / 서명 없음
        String maliciousToken = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "."
                              + base64Url("{\"sub\":\"attacker\",\"role\":\"ROLE_ADMIN\"}") + ".";
        System.out.println("  악의 토큰 = " + maliciousToken);

        try {
            jwt.verify(maliciousToken);
            System.out.println("  💥 통과 — 라이브러리 취약 (서명 없음 알고리즘 허용)");
        } catch (Exception e) {
            System.out.println("  ✓ 차단: " + e.getClass().getSimpleName());
            System.out.println("    → 핵심: 차단 자체. 정확한 예외 타입은 버전에 따라 다름");
            System.out.println("      (UnsupportedJwtException / MalformedJwtException / SecurityException 류)");
        }

        // ────────────────────────────────────────
        // (2) 만료 토큰 검증
        // ────────────────────────────────────────
        MeasurementLog.section("(2) 만료 — 2 초 만료 토큰 + 3 초 sleep");
        String shortToken = jwt.createShortLivedToken("alice", 2);
        System.out.println("  발급 직후 검증:");
        try {
            jwt.verify(shortToken);
            System.out.println("    ✓ 유효");
        } catch (Exception e) {
            System.out.println("    " + e.getClass().getSimpleName());
        }

        System.out.println("  3 초 sleep ...");
        Thread.sleep(3000);

        System.out.println("  만료 후 검증:");
        try {
            jwt.verify(shortToken);
            System.out.println("    💥 통과 — 만료 검증 실패");
        } catch (Exception e) {
            System.out.println("    ✓ 차단: " + e.getClass().getSimpleName());
            System.out.println("    → jjwt 가 exp 자동 검증");
        }

        // ────────────────────────────────────────
        // (3) 위조 서명 — verifyWith 가 차단
        // ────────────────────────────────────────
        MeasurementLog.section("(3) 위조 서명 — verifyWith(key) 가 차단");
        System.out.println("  위조 시도: secret 모르고 가짜 서명 붙임");
        // exp 같은 클레임은 평범한 값 — 핵심은 "서명 불일치"
        String forgedToken = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}") + "."
                           + base64Url("{\"sub\":\"alice\",\"role\":\"ROLE_ADMIN\"}") + "."
                           + "fake-signature-not-from-secret";
        try {
            jwt.verify(forgedToken);
            System.out.println("    💥 통과 (보안 사고)");
        } catch (Exception e) {
            System.out.println("    ✓ 차단: " + e.getClass().getSimpleName());
            System.out.println("    → 서명 불일치 — verifyWith(key) 의 서명 검증 단계가 거부");
        }
        System.out.println();
        System.out.println("  ⚠️ 진짜 위험: 서명 검증 단계 자체를 생략하고 (예: Base64 디코드로 payload 만 신뢰)");
        System.out.println("     그 값으로 인가 판단 → 위조 통과. Stage2_1 (3) 의 디코드 출력 참고.");

        System.out.println();
        System.out.println("[학습]");
        System.out.println("  · alg=none — 라이브러리 화이트리스트 검증 확인");
        System.out.println("  · 만료 — exp 자동 검증. 미설정 = 영구 토큰 = 도난 시 영구 사용");
        System.out.println("  · 서명 — verifyWith(key) 명시. parseUnsecuredClaims 절대 금지");
        System.out.println("  · Bearer 평문 — HTTPS 필수");
    }

    private static String base64Url(String s) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes());
    }
}
