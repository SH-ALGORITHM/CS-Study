package jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * 학습용 JWT 서비스. jjwt 0.12+ 권장 API.
 *
 * <h3>주의</h3>
 * 실무 secret 은 환경변수 / Vault. 코드에 박지 말 것 (이 데모는 학습용).
 */
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(String secret, long expirationMinutes) {
        // HS256 요구 — 최소 256 비트 (32 byte). 학습용 32 byte 이상 명시
        if (secret.getBytes().length < 32) {
            throw new IllegalArgumentException("secret must be >= 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMinutes = expirationMinutes;
    }

    public String createToken(String subject, String role) {
        return Jwts.builder()
            .subject(subject)
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES)))
            .signWith(key)
            .compact();
    }

    /** 짧은 만료 토큰 — Stage2_2 함정 시연용 */
    public String createShortLivedToken(String subject, long expirationSeconds) {
        return Jwts.builder()
            .subject(subject)
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plus(expirationSeconds, ChronoUnit.SECONDS)))
            .signWith(key)
            .compact();
    }

    public Jws<Claims> verify(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token);
    }
}
