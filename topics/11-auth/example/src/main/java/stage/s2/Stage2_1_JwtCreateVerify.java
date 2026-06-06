package stage.s2;

import infra.MeasurementLog;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jwt.JwtService;

/**
 * STAGE 2-1 — JWT 발급 + 검증 + 구조 관찰.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>토큰 구조 — header.payload.signature (Base64URL)</li>
 *   <li>같은 secret 으로 발급 / 검증</li>
 *   <li>payload 는 디코드 가능 (비밀 아님) — jwt.io 에 붙여 확인 가능</li>
 *   <li>서명 검증으로 위조 방지</li>
 * </ul>
 */
public class Stage2_1_JwtCreateVerify {

    public static void main(String[] args) {
        MeasurementLog.title("STAGE 2-1 — JWT 발급 + 검증");

        String secret = "this-is-a-32-byte-secret-for-hs256!!";
        JwtService jwt = new JwtService(secret, 15);   // 15 분 만료

        MeasurementLog.section("(1) 토큰 발급");
        String token = jwt.createToken("alice@example.com", "ROLE_USER");
        System.out.println("  token = " + token);

        MeasurementLog.section("(2) 토큰 구조 — header.payload.signature");
        String[] parts = token.split("\\.");
        System.out.println("  header    = " + parts[0]);
        System.out.println("  payload   = " + parts[1]);
        System.out.println("  signature = " + parts[2]);

        MeasurementLog.section("(3) payload Base64URL 디코드 — 비밀 아님");
        java.util.Base64.Decoder decoder = java.util.Base64.getUrlDecoder();
        String payloadJson = new String(decoder.decode(parts[1]));
        System.out.println("  " + payloadJson);

        MeasurementLog.section("(4) 검증 — 같은 secret 으로 OK");
        Jws<Claims> jws = jwt.verify(token);
        Claims claims = jws.getPayload();
        System.out.println("  subject = " + claims.getSubject());
        System.out.println("  role    = " + claims.get("role"));
        System.out.println("  exp     = " + claims.getExpiration());

        MeasurementLog.section("(5) 다른 secret 으로 검증 시도 → 실패");
        JwtService wrong = new JwtService("WRONG-32-byte-secret-for-hs256!!", 15);
        try {
            wrong.verify(token);
            System.out.println("  통과 — 보안 사고!");
        } catch (Exception e) {
            System.out.println("  " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        System.out.println();
        System.out.println("[학습]");
        System.out.println("  · JWT payload 는 디코드 가능 — 비밀 절대 X");
        System.out.println("  · 서명만이 위조 방지의 핵심");
        System.out.println("  · jwt.io 에서 토큰 붙여서 시각화 확인");
    }
}
