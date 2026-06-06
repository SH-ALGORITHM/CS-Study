# 11주차 Spring Security + 세션 vs JWT + 인가 — 용어 정리

> 10 주차와 같은 형식.

---

## 🔐 인증 / 인가 본질

| 용어 | 풀어쓰면 |
|---|---|
| **Authentication** (인증) | "누구냐". 비밀번호 / 토큰으로 신원 증명 |
| **Authorization** (인가) | "뭘 할 수 있나". 권한 검사. 인증 후 |
| **Principal** | 인증된 사용자 객체 |
| **Credential** | 인증 자료. 비밀번호 / 토큰 |
| **GrantedAuthority** | 권한 객체. `ROLE_ADMIN` / `READ_PRIVILEGE` |
| **SecurityContext** | 현재 스레드의 인증 정보. ThreadLocal 기반 (5 주차 회수) |
| **SecurityContextHolder** | SecurityContext 의 정적 접근자 |

## 🛡 Spring Security 6

| 용어 | 풀어쓰면 |
|---|---|
| **`SecurityFilterChain`** | Spring 6 의 기본 보안 설정. Servlet Filter 체인 |
| **`HttpSecurity`** 람다 DSL | Spring 6+ 권장 — `http.authorizeHttpRequests(...).build()` |
| **`WebSecurityConfigurerAdapter`** | Spring 5.7+ deprecated, Spring 6 제거됨. 사용 X |
| **`@EnableWebSecurity`** | 보안 활성화 |
| **`@EnableMethodSecurity`** | 메서드 단 인가 (`@PreAuthorize` 등) 활성화 |
| **`UserDetailsService`** | DB 사용자 조회 인터페이스. `loadUserByUsername` |
| **`UserDetails`** | Spring Security 의 사용자 객체 |
| **`AuthenticationManager`** | 인증 처리 핵심. `ProviderManager` 가 표준 구현 |
| **`AuthenticationProvider`** | 실제 인증 로직. `DaoAuthenticationProvider` (UserDetailsService 사용) |
| **`PasswordEncoder`** | 비밀번호 해시. `BCryptPasswordEncoder` 표준 |
| **BCrypt** | salt 자동 + cost factor. 같은 평문 → 매번 다른 해시 |
| **`UsernamePasswordAuthenticationFilter`** | Form 로그인 처리 Filter |

## 🍪 세션

| 용어 | 풀어쓰면 |
|---|---|
| **세션** | 서버에 상태 보관. 클라이언트는 `JSESSIONID` 쿠키만 |
| **`JSESSIONID`** | 세션 식별 쿠키. 기본 이름 |
| **세션 고정** (Fixation) | 공격자가 미리 만든 세션 ID 를 피해자에게 강제. Spring Security 가 로그인 시 ID 재발급으로 방어 |
| **sticky session** | 로드밸런서가 같은 사용자를 같은 서버로. 다중 인스턴스 임시 해결 |
| **`@EnableRedisHttpSession`** | Spring Session — 세션 저장소를 Redis 로 (9 주차 회수) |
| **`SessionCreationPolicy`** | ALWAYS / IF_REQUIRED (기본) / NEVER / STATELESS |
| **`STATELESS`** | 세션 안 만듦. JWT 환경에서 권장 |

## 🎫 JWT

| 용어 | 풀어쓰면 |
|---|---|
| **JWT** (JSON Web Token) | 자체 서명된 토큰. RFC 7519 |
| **JWT 구조** | `header.payload.signature` Base64URL |
| **Claims** | payload 의 키-값 |
| **`sub`** (subject) | 사용자 식별자 (보통 user id 또는 email) |
| **`exp`** (expiration) | 만료 시각. 검증 필수 |
| **`iat`** (issued at) | 발급 시각 |
| **`iss`** (issuer) | 발급자 |
| **`aud`** (audience) | 대상 |
| **알고리즘** | HS256 (HMAC + SHA256) / RS256 (RSA + SHA256) / ES256 (ECDSA) |
| **`alg=none`** | 서명 없음. 공격 — 라이브러리가 화이트리스트 검증으로 방어 |
| **`Bearer Token`** | `Authorization: Bearer <jwt>` 헤더 표준 |
| **access token** | 짧은 만료 (15 분). API 호출용 |
| **refresh token** | 긴 만료 (7 일). access 재발급용 |
| **Refresh 토큰 회전** | 매번 새 refresh + 옛 refresh 즉시 무효 (도난 감지) |
| **JWT 블랙리스트** | 로그아웃 시 토큰을 Redis 에 기록. Stateless 가치를 깎음 — 트레이드오프 |
| **jjwt** | Java JWT 라이브러리. 0.12+ 권장 |
| **nimbus-jose-jwt** | Spring Security OAuth2 가 사용 |

## ⚠️ JWT 함정

| 용어 | 풀어쓰면 |
|---|---|
| **`alg=none` 공격** | 헤더 조작 — `{"alg":"none"}` + 서명 빈 문자열. 라이브러리가 알고리즘 화이트리스트 |
| **서명 검증 누락** | 토큰 파싱만 하고 검증 안 함. `verifyWith(key)` 명시 |
| **만료 미설정** | 영구 토큰 = 도난 시 영구 사용. `exp` 필수 |
| **Bearer 평문 전송** | HTTP 평문 = 토큰 도난 즉시. HTTPS 필수 |
| **secret 노출** | 코드 / 리포 / 로그에 secret 노출 = 누구나 토큰 발급 가능 |
| **알고리즘 confusion** | HS256 + RSA 공개키를 secret 으로 사용 = 위조. 명시 검증 |

## 🎯 인가 (@PreAuthorize)

| 용어 | 풀어쓰면 |
|---|---|
| **`@PreAuthorize`** | 메서드 진입 전 SpEL 평가 |
| **`@PostAuthorize`** | 메서드 종료 후 결과로 SpEL 평가 |
| **`@PreFilter` / `@PostFilter`** | 컬렉션 인자 / 결과 필터링 |
| **`hasRole('X')`** | 권한 검사 — `ROLE_X` 자동 prefix |
| **`hasAuthority('X')`** | prefix 없이 정확한 권한 |
| **`authentication`** | SpEL 안의 현재 Authentication 객체 |
| **`principal`** | `authentication.principal` 줄임 |
| **`#param`** | 메서드 인자 참조 |
| **`#root.method.name`** | 메서드 메타 |
| **5 주차 회수** | `@PreAuthorize` 도 AOP 프록시. self-invocation 함정 동일 |
| **`RoleHierarchy`** | 권한 계층. `ROLE_ADMIN > ROLE_USER` 자동 상속 |

## 🌐 OAuth 2.0

| 용어 | 풀어쓰면 |
|---|---|
| **OAuth 2.0** | 외부 인증 위임 표준 |
| **Authorization Code Flow** | 표준 흐름. code → token 교환 |
| **Implicit Flow** | 권장 X (보안 약함) |
| **Client Credentials Flow** | 서버 간 인증 |
| **Resource Owner Password Flow** | 권장 X (비밀번호 직접 전달) |
| **`state` 파라미터** | CSRF 방어. 요청 / 콜백 일치 |
| **PKCE** | Proof Key for Code Exchange. code 가로채기 방어. 모바일 / SPA 필수 |
| **code_verifier / code_challenge** | PKCE 의 쌍. challenge = SHA256(verifier) |
| **scope** | 요청 권한 범위 (email / profile / ...) |
| **id_token** (OIDC) | OAuth 의 사용자 정보 토큰. JWT |
| **OIDC** (OpenID Connect) | OAuth 2.0 위에 인증 표준 |
| **Spring Security OAuth2 Client** | `spring-boot-starter-oauth2-client` |

## 🛡 CSRF / CORS

| 용어 | 풀어쓰면 |
|---|---|
| **CSRF** (Cross-Site Request Forgery) | 다른 사이트가 내 인증 빌려서 요청 |
| **CSRF 토큰** | 세션 인증에서 매 요청에 토큰 추가. JWT (헤더) 는 자연 회피 |
| **`SameSite` 쿠키** | CSRF 방어. Strict / Lax / None |
| **CORS** (Cross-Origin Resource Sharing) | 브라우저의 다른 origin 호출 제한 |
| **preflight** | CORS 의 OPTIONS 요청 |
| **`allowCredentials`** | 인증 헤더 / 쿠키 보낼지 |

## 🧱 학습 도구

| 용어 | 풀어쓰면 |
|---|---|
| **`spring-boot-starter-security`** | Spring Security |
| **`spring-session-data-redis`** | Redis 세션 저장 |
| **`spring-boot-starter-oauth2-client`** | OAuth2 클라이언트 |
| **`io.jsonwebtoken:jjwt-api`** | jjwt API |
| **jwt.io** | JWT 디코딩 / 검증 도구 (학습용) |

---

## ★ STAGE 1 진입 관문

1. 세션 vs JWT 본질 차이 + 각각의 장단
2. JWT 5 함정 (alg=none / 서명 / 만료 / Refresh / Bearer 평문)
3. `@PreAuthorize` 가 5 주차 `@Aspect` 와 같은 메커니즘인 이유

## ★ STAGE 2 진입 관문

1. Spring Security Filter 체인 순서 + 인증 실패 흐름
2. BCrypt 가 MD5 / SHA 보다 좋은 이유 (salt + cost factor)
3. Redis 세션 + 9 주차 캐시 — 무엇을 어디에
