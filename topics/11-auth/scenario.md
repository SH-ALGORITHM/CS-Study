# 11주차 — "누구냐" 와 "뭘 할 수 있나" (Spring Security + 세션 vs JWT + 인가)

이번 주제: 10 주차까지 외부 호출 부하 / 캐시 / 인덱스로 시스템 안정성을 다뤘다면, 11 주차는 **매 요청마다 신원을 확인하는 메커니즘**. 세션 (`JSESSIONID` + 서버 메모리) 과 JWT (Stateless 토큰) 의 트레이드오프, Spring Security 의 SecurityFilterChain, `@PreAuthorize` 의 SpEL (5 주차 AOP 회수), Redis 세션 (9 주차 회수), OAuth 외부 호출 (10 주차 회수) 까지. 시리즈의 종합 자리.

5 가지 학습 축:
- **Authentication vs Authorization** — 인증 (누구) / 인가 (뭘 할 수 있나). Filter / Interceptor / AOP 어디서 처리
- **세션 vs JWT** ★ — 상태 보관 위치. 서버 메모리 / Redis 공유 / 토큰 안에 직접. 9 주차 Redis 결합
- **Spring Security 6 기본** — `SecurityFilterChain` 람다 DSL / `UserDetailsService` / `PasswordEncoder` (BCrypt) / Filter 체인 순서
- **JWT 함정** ★★ — `alg=none` / 서명 검증 누락 / 만료 처리 / Refresh 토큰. 면접 직결
- **`@PreAuthorize` SpEL + OAuth 2.0** — 5 주차 AOP 회수 + 10 주차 외부 호출 직결 (Authorization Code Flow + state + PKCE)

---

## 우선 알아둬야 할 단어 (시작 전 1 분)

| 단어 | 풀어쓰면 |
|---|---|
| **Authentication** (인증) | "누구냐". 비밀번호 / 토큰으로 신원 증명 |
| **Authorization** (인가) | "뭘 할 수 있나". 권한 검사. 인증 후 |
| **Principal** | 인증된 사용자 객체. `Authentication.getPrincipal()` |
| **SecurityFilterChain** | Spring Security 6 의 기본. Servlet Filter 체인 |
| **`UserDetailsService`** | DB 에서 사용자 조회 인터페이스. `loadUserByUsername` |
| **`PasswordEncoder`** | 비밀번호 단방향 해시. **BCrypt 표준** |
| **세션** (Session) | 서버에 상태 보관. 클라이언트는 `JSESSIONID` 쿠키만 |
| **JWT** (JSON Web Token) | 자체 서명된 토큰. `header.payload.signature` Base64URL |
| **Claims** | JWT payload 의 키-값. `sub` / `exp` / `iat` 등 |
| **Stateless** | 서버에 상태 없음. JWT 의 핵심 가치 (수평 확장) |
| **Bearer Token** | `Authorization: Bearer <token>` 헤더 표준 |
| **`@PreAuthorize`** | 메서드 인가 어노테이션. SpEL — `"hasRole('ADMIN') and #userId == authentication.name"` |
| **OAuth 2.0** | 외부 인증 위임 표준. Google / Kakao login |
| **CSRF** (Cross-Site Request Forgery) | 다른 사이트가 내 인증 빌려서 요청. 세션 인증에서 위험 |
| **CORS** (Cross-Origin Resource Sharing) | 브라우저의 다른 origin 호출 제한 |

> 📚 더 깊은 용어 (Filter 순서 / SecurityContext / OAuth flow 별 / Refresh 토큰 회전 등) — [`terms.md`](terms.md) 참고.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 0. 개념 숙지 (도메인 무관)
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 필수 개념

### 10 주차 → 11 주차 연결
1. **10 주차까지** 외부 호출 부하 / 캐시 / 인덱스로 시스템 안정성. 11 주차는 신원 확인 자체
2. **OAuth = 외부 호출** — 10 주차 직속 연장. 외부 Provider 호출 = timeout / Circuit Breaker 그대로

### 세션 vs JWT 본질
3. **세션** — 서버 메모리에 사용자 상태 보관. 클라이언트는 `JSESSIONID` 쿠키만. 무효화 즉시 가능 / 다중 인스턴스 공유 어려움 (sticky session 또는 Redis 세션)
4. **JWT** — 토큰 안에 사용자 정보 직접 + 서버 서명. 서버 상태 없음 (Stateless) / 만료 전 무효화 불가 (블랙리스트로 우회) / 수평 확장 자연
5. **9 주차 회수** — Redis 를 세션 저장소로. 다중 인스턴스 공유 + 즉시 무효화

### Spring Security 6 기본
6. **`SecurityFilterChain`** 람다 DSL — `httpSecurity.authorizeHttpRequests(...).formLogin(...).build()`. Spring 6+ 람다 권장
7. **`UserDetailsService` + `PasswordEncoder`** — DB 사용자 조회 + BCrypt 해시 검증. `AuthenticationManager` 가 연결
8. **Filter 체인 순서** — 보안 Filter 가 컨트롤러 진입 전. 인증 실패 시 컨트롤러 안 감

### JWT 함정 (★ 면접 직결)
9. **`alg=none` 공격** — JWT 헤더 조작으로 "서명 없음" 강제. 라이브러리가 알고리즘 화이트리스트 검증해야
10. **서명 검증 누락** — 토큰 파싱만 하고 검증 안 함 → 누구나 토큰 만들기 가능
11. **만료 처리** — `exp` 클레임 검증 필수. 미설정 = 영구 토큰. 보통 access 15 분 / refresh 7 일
12. **Refresh 토큰 회전** — 매번 새 refresh + 옛 refresh 즉시 무효 (도난 감지)
13. **Bearer Token + HTTPS 필수** — 토큰 자체엔 비밀이 들어있지 않다고 가정. HTTP 평문 = 도난 즉시

### `@PreAuthorize` SpEL (5 주차 AOP 회수)
14. **`@EnableMethodSecurity`** + `@PreAuthorize("hasRole('ADMIN')")` — 5 주차 `@Aspect` 와 같은 프록시 메커니즘
15. **self-invocation 함정** — `this.adminOnly()` 호출 시 우회. 5, 6, 7, 9 주차 모두 같은 메커니즘
16. **SpEL 동적** — `@PreAuthorize("#userId == authentication.name")` — 본인만 자기 데이터 수정

### OAuth 2.0 (10 주차 외부 호출 회수)
17. **Authorization Code Flow** — 사용자가 Google 로그인 → code 받음 → 서버가 code → access_token 교환. 외부 호출 (10 주차)
18. **`state` 파라미터** — CSRF 방어. 요청 / 콜백 일치 검증
19. **PKCE** (Proof Key for Code Exchange) — code 가로채기 방어. 모바일 / SPA 필수

## 자기 검증 (입으로)

**★ 관문 3**
- [ ] ★ 세션 vs JWT 본질 차이 — 본인 말로 1 분. 각각의 장단
- [ ] ★ JWT 함정 5 가지 (alg=none / 서명 누락 / 만료 / Refresh / Bearer 평문)
- [ ] ★ `@PreAuthorize` 가 5 주차 `@Aspect` 와 같은 메커니즘인 이유

**보너스**
- [ ] Spring Security 의 Filter 체인 순서 + 인증 실패 시 흐름
- [ ] BCrypt 가 MD5 / SHA-256 보다 좋은 이유 (salt + cost factor)
- [ ] Redis 세션 + 9 주차 캐시 — 무엇을 어디에
- [ ] OAuth 의 state / PKCE 각각 막는 공격
- [ ] Refresh 토큰 회전 vs 단순 발급
- [ ] CSRF / CORS 가 세션 / JWT 어느 쪽에서 더 중요한가
- [ ] `@PreAuthorize` self-invocation 함정 (5, 6, 7, 9 주차 회수)


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 1. 본인 도메인 선택
━━━━━━━━━━━━━━━━━━━━━━━━━━

11 주차 학습 포인트는 **회원 / 권한 / 인증이 자연스러운 도메인** 에서 잘 드러난다.

## 후보 도메인 (12 개)

| # | 도메인 | 권한 자연 | 인증 흐름 자연 | 면접 가치 | 메모 |
|---|---|---|---|---|---|
| 1 | **회원 + 관리자** (`member_admin`) | ★★★ | ★★★ | ★★★ | 가장 정석. ROLE_USER / ROLE_ADMIN |
| 2 | **이커머스 주문** (`order`) | ★★★ | ★★ | ★★★ | 본인 주문만 조회 = `@PreAuthorize` SpEL |
| 3 | **게시판 + 작성자 권한** (`board`) | ★★★ | ★★ | ★★★ | 본인 글만 수정 / 삭제 — SpEL 정석 |
| 4 | **결제 / 환불** (`payment`) | ★★★ | ★★★ | ★★★ | 본인 결제만 환불. 다중 권한 |
| 5 | **API 키 인증** (`api_key`) | ★★ | ★★★ | ★★ | JWT 외 또 다른 토큰. Bearer 패턴 |
| 6 | **OAuth 소셜 로그인** (`oauth`) | ★★ | ★★★ | ★★★ | 10 주차 외부 호출 직결 |
| 7 | **다중 권한 어드민** (`multi_role`) | ★★★ | ★★ | ★★ | EDITOR / VIEWER / ADMIN 계층 |
| 8 | **세션 vs JWT 비교** (`session_vs_jwt`) | ★★ | ★★★ | ★★★ | 같은 도메인에 두 방식. 트레이드오프 직접 |
| 9 | **2FA** (`two_factor`) | ★★ | ★★★ | ★★ | TOTP / SMS. 다단계 인증 |
| 10 | **SSO** (`sso`) | ★★ | ★★★ | ★★ | 여러 서비스 공유 로그인 |
| 11 | **회원 가입 + 이메일 인증** (`signup_verify`) | ★★ | ★★★ | ★★ | 6 주차 Event 연장 |
| 12 | **권한 위임 / 토큰** (`delegation`) | ★★ | ★★ | ★★ | API 위임 (OAuth 의 핵심 동기) |

## 학습자 프로필별 추천

| 본인 상황 | 추천 |
|---|---|
| 입문자 | **1 회원 + 관리자** — 가장 정석 |
| 본인 도메인 (게시판 / 주문) 연장 | **3 게시판** / **2 주문** |
| 면접 가치 | **1 / 3 / 4 / 8** |
| 6 주차 Event 연장 | **11 가입 + 이메일 인증** |
| 10 주차 외부 호출 직결 | **6 OAuth** |


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 2. 클래스 구조 매핑
━━━━━━━━━━━━━━━━━━━━━━━━━━

| 도메인 | Entity | Service | 인증 흐름 |
|---|---|---|---|
| 1 회원 + 관리자 | Member + Role | MemberService | 가입 → BCrypt 해시 → 로그인 → 세션/JWT |
| 3 게시판 | Post + Member | PostService | 작성자만 수정 — `@PreAuthorize("#post.member.id == authentication.name")` |
| 4 결제 | Payment + Member | PaymentService | 본인 결제만 환불 — 같은 패턴 |
| 8 세션 vs JWT | Member | AuthService | 같은 도메인 두 엔드포인트 (`/login-session` / `/login-jwt`) |


━━━━━━━━━━━━━━━━━━━━━━━━━━
환경
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Java 21
- Spring Boot 3.2 + Spring Security 6
- jjwt 0.12+ — JWT 라이브러리
- H2 인메모리 + Redis (9 주차 docker 재활용)

## build.gradle

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // 세션 — Redis 저장소
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.session:spring-session-data-redis'

    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.5'

    runtimeOnly 'com.h2database:h2'
}
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
STAGE 진행 가이드
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 예상 학습 시간

| 단계 | 시간 | 비고 |
|---|---|---|
| STAGE 1 (Spring Security 기본 — Form Login + BCrypt) | 2 ~ 3 시간 | **화** |
| **STAGE 2 (세션 vs JWT + JWT 함정)** ★ | **2 ~ 3 시간** | **목**. 가장 중요 |
| STAGE 3 (인가 — @PreAuthorize + 5 주차 회수) | 1 ~ 2 시간 | |
| STAGE 4 [시나리오만] OAuth 2.0 (살짝) | 1 시간 | example 없음 |
| **합계** | **6 ~ 9 시간** | |
| STAGE 5 [여유] 토큰 무효화 + 12 주차 브릿지 | 30 ~ 60 분 | |

### [화 11:00] — STAGE 1

#### ▸ STAGE 1 — Spring Security 6 기본 (필수)

##### 1-1. SecurityFilterChain — 람다 DSL

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/signup").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/"))
            .logout(out -> out.logoutSuccessUrl("/"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**관찰 포인트**:
- Spring 6 의 람다 DSL — 옛 `WebSecurityConfigurerAdapter` 상속 제거됨
- `authorizeHttpRequests` 가 표준 (옛 `authorizeRequests` deprecated)
- BCrypt — salt 자동 + cost factor 조정 가능 (기본 10)

##### 1-2. UserDetailsService + 회원 가입 / 로그인

```java
@Service
public class MemberService implements UserDetailsService {
    private final MemberRepository repo;
    private final PasswordEncoder encoder;

    public Member signup(String email, String rawPassword) {
        String hashed = encoder.encode(rawPassword);
        return repo.save(new Member(email, hashed, "ROLE_USER"));
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        Member m = repo.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException(email));
        return User.withUsername(m.getEmail())
            .password(m.getPasswordHash())
            .roles(m.getRole().replace("ROLE_", ""))
            .build();
    }
}
```

**관찰 포인트**:
- BCrypt 해시 — 같은 평문 → 매번 다른 해시 (salt). `matches()` 로 검증
- `UserDetails` 가 Spring Security 의 표준 사용자 객체

##### 1-3. Filter 체인 순서 + 인증 실패

```java
// /admin 으로 ROLE_USER 가 접근 → AccessDeniedException → 403
// /private 으로 미인증 접근 → 로그인 페이지로 리다이렉트
```

**관찰 포인트**:
- 보안 Filter 가 컨트롤러 앞 — 인증 실패 시 컨트롤러 안 감
- `SecurityFilterChain` 의 매처 순서가 매칭 우선순위


### [목 11:00] — STAGE 2 ~ STAGE 3

#### ▸ STAGE 2 — 세션 vs JWT + JWT 함정 (★ **11 주차 가장 중요**)

##### 2-1. 세션 기반 — `JSESSIONID` + 서버 메모리

```java
// 로그인 성공 시 자동으로 세션 생성 + JSESSIONID 쿠키
// Spring Security 가 SecurityContext 를 세션에 저장
```

**관찰 포인트**:
- 브라우저 개발자 도구 — Cookies 에 `JSESSIONID` 확인
- 서버 재시작 = 모든 세션 손실
- 다중 인스턴스 = 각자 메모리 → sticky session 또는 공유 저장소

##### 2-2. Redis 세션 — 9 주차 회수

```java
@EnableRedisHttpSession
public class SessionConfig {
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }
}
```

**관찰 포인트**:
- 9 주차 Redis 그대로 — 세션 저장소로 활용
- `redis-cli` 로 `keys *session*` 확인
- 다중 인스턴스 공유 + 즉시 무효화 가능

##### 2-3. JWT 기반 — Stateless

```java
@Service
public class JwtService {
    private final SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());

    public String createToken(String email, String role) {
        return Jwts.builder()
            .subject(email)
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
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
```

**관찰 포인트**:
- 토큰 자체에 정보 — 디코드 사이트 (jwt.io) 로 payload 확인 가능
- 서버 상태 없음 — 토큰만 유효하면 OK
- 만료 전 무효화 어려움 — 블랙리스트로 우회

##### 2-4. JWT 함정 ★★

**(a) `alg=none` 공격**:
```
헤더 조작 — {"alg":"none","typ":"JWT"} + 서명 빈 문자열
라이브러리가 검증 안 하면 통과
```
**방어** — 라이브러리가 알고리즘 화이트리스트 검증. jjwt 0.12+ 는 기본 차단

**(b) 서명 검증 누락**:
```java
// 잘못된 코드 — parser().parse() 가 검증 안 함
Claims c = Jwts.parser().parseSignedClaims(token).getPayload();
// 정답 — verifyWith(key) 명시
```

**(c) 만료 미설정**:
```java
.expiration(null)   // ← 영구 토큰. 도난 시 영구 사용 가능
```

**(d) Refresh 토큰 회전**:
```
매번 새 refresh 발급 + 옛 refresh 즉시 무효 (DB / Redis 에 저장)
도난된 refresh 사용 시 즉시 감지 가능
```

**(e) Bearer 평문**:
```
HTTPS 필수. HTTP 평문 = 네트워크 도청 = 토큰 도난 즉시
```


#### ▸ STAGE 3 — `@PreAuthorize` + 5 주차 AOP 회수 (필수)

##### 3-1. `@EnableMethodSecurity` + 어노테이션

```java
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {}

@Service
public class PostService {
    @PreAuthorize("hasRole('USER')")
    public Post findOne(Long id) { /* ... */ }

    @PreAuthorize("#post.member.email == authentication.name")
    public void update(Post post) { /* 본인 글만 */ }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteAny(Long id) { /* 관리자만 */ }
}
```

**관찰 포인트**:
- 5 주차 `@Aspect` 와 같은 프록시 메커니즘
- SpEL — 메서드 인자 / `authentication` / `principal` 참조
- self-invocation 함정 — `this.update()` 는 우회 (5, 6, 7, 9 주차와 동일)

##### 3-2. URL 보안 vs Method 보안

```java
// URL 보안 (SecurityFilterChain)
.requestMatchers("/admin/**").hasRole("ADMIN")

// Method 보안 (@PreAuthorize)
@PreAuthorize("hasRole('ADMIN')")
public void delete(Long id) { ... }
```

| 축 | URL 보안 | Method 보안 |
|---|---|---|
| 위치 | Filter 체인 | AOP 프록시 |
| 표현력 | 단순 (URL 패턴) | SpEL — 인자 / 동적 |
| 적용 | 컨트롤러 진입 전 | 메서드 진입 직전 |
| 5 주차 메커니즘 | Servlet Filter | `@Aspect` |

##### 3-3. self-invocation 함정 (5, 6, 7, 9 주차 회수)

```java
@Service
public class PostService {
    @PreAuthorize("hasRole('ADMIN')")
    public void adminOnly() { /* ... */ }

    public void wrapper() {
        this.adminOnly();   // ← AOP 우회. 권한 검사 X !!
    }
}
```

**해결**: 다른 빈 호출 / 자기 자신 주입 (5 주차 패턴)


━━━━━━━━━━━━━━━━━━━━━━━━━━
─── 선택 ───
━━━━━━━━━━━━━━━━━━━━━━━━━━

### [선택] ▸ STAGE 4 — OAuth 2.0 (10 주차 외부 호출 직결, 시나리오만)

##### 4-1. Authorization Code Flow

```
1. 사용자: "Google 로 로그인" 클릭
2. 브라우저 → Google authorize URL (redirect)
3. 사용자 → Google 로그인
4. Google → 콜백 URL?code=xxx&state=yyy
5. 서버 → Google /token POST { code, client_id, client_secret }
6. Google → { access_token, id_token, refresh_token }
7. 서버 → 사용자 정보 조회 + 자체 JWT 발급
```

**10 주차 결합**:
- 5, 6, 7 단계는 외부 호출 — timeout / Circuit Breaker 그대로 적용
- Google 다운 시 fallback (사용자에게 "다시 시도")

##### 4-2. `state` + PKCE

```
state — CSRF 방어. 요청 시 무작위 문자열 → 콜백에서 일치 검증
PKCE  — code 가로채기 방어. 모바일 / SPA 필수
        code_verifier (랜덤) + code_challenge (해시) 쌍
```

##### 4-3. Spring Security OAuth2 Client

```gradle
implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
```

```properties
spring.security.oauth2.client.registration.google.client-id=...
spring.security.oauth2.client.registration.google.client-secret=...
spring.security.oauth2.client.registration.google.scope=email,profile
```

> ⚠️ 실 시연은 Google 클라이언트 ID 필요. 학습자 부담 → 본 example 에서는 시연 X. 본인 도메인에서 선택 적용.


### [선택] ▸ STAGE 5 — 토큰 무효화 + 12 주차 (관측) 브릿지

##### 5-1. JWT 블랙리스트 (Redis)

```java
// 로그아웃 시
redis.set("blacklist:" + token, "1", expiration);

// 인증 시
if (redis.exists("blacklist:" + token)) reject();
```

##### 5-2. 12 주차 (관측) 예고

- Spring Security 메트릭 — 로그인 성공 / 실패 / 권한 거부 횟수
- Micrometer + Prometheus + Grafana


━━━━━━━━━━━━━━━━━━━━━━━━━━
금지 키워드 — STAGE 1 ~ 2
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Spring Security 의 옛 `WebSecurityConfigurerAdapter` — Spring 6 에서 제거됨
- JWT 라이브러리 비교 — jjwt / nimbus / auth0 — 본 학습 후
- OAuth 2.1 / OIDC — 본 학습 범위 밖
- SAML — 본 학습 후
- mTLS / Certificate 인증 — 본 학습 후


━━━━━━━━━━━━━━━━━━━━━━━━━━
학습 기록
━━━━━━━━━━━━━━━━━━━━━━━━━━

### 10 주차 회상 — 11 주차로

| 10 주차 | 11 주차 |
|---|---|
| 외부 호출 부하 제어 | 외부 인증 (OAuth) — 같은 외부 호출 |
| Timeout + Circuit Breaker | OAuth provider 호출에도 적용 |
| 9 주차 캐시와 결합 | 9 주차 Redis = 세션 저장소 + JWT 블랙리스트 |
| 5 주차 @Order 회수 | @PreAuthorize 도 AOP — 5 주차 직속 회수 |

### 면접 단골
- **"세션 vs JWT"** — 상태 보관 위치. 세션 = 무효화 즉시 / JWT = Stateless 수평 확장
- **"JWT 의 5 함정"** — alg=none / 서명 누락 / 만료 / Refresh / Bearer 평문
- **"BCrypt vs SHA"** — salt 자동 + cost factor + 시간 비용
- **"`@PreAuthorize` self-invocation"** — 5, 6, 7, 9 주차와 같은 프록시 메커니즘
- **"OAuth state / PKCE"** — CSRF / code 가로채기 방어
- **"Refresh 토큰 회전"** — 도난 감지 + 옛 refresh 즉시 무효
- **"Spring Security Filter 체인"** — 컨트롤러 앞 인증 / 실패 시 컨트롤러 안 감
- **"CSRF / CORS 차이"** — CSRF = 다른 사이트 위조 요청 / CORS = 브라우저 origin 제한
- **"세션 + 9 주차 Redis"** — sticky session 없이 다중 인스턴스 공유

### 실무 확장 화두
- **JWT 블랙리스트의 모순** — JWT 의 Stateless 가치를 깎음. 짧은 access + 긴 refresh 가 더 깔끔
- **OAuth provider 다운 시 fallback** — 10 주차 Circuit Breaker 결합
- **Refresh 토큰 회전의 동시성** — 클라이언트 2 개가 동시 갱신 시도 → 한쪽 실패. 클라이언트 단 lock
- **세션 고정 (Session Fixation) 공격** — 로그인 시 세션 ID 재발급 필수
- **`@PreAuthorize` + 5 주차 `@Order` 양파** — 트랜잭션 / 캐시 / 권한 순서
- **OAuth Authorization Code Flow vs Implicit Flow** — Implicit 는 권장 X (보안 약함). Code + PKCE 가 표준
- **CSRF 토큰** — 세션 인증에 필수. JWT (헤더 전송) 는 자연 회피
- **6 주차 Event + 로그인 감사** — `AuthenticationSuccessEvent` / `AuthenticationFailureBadCredentialsEvent`
- **다중 권한 계층** — `RoleHierarchy` — ADMIN → USER 자동 상속
- **9 주차 캐시 + 권한** — 사용자별 캐시 키 (`@Cacheable(key = "#userId")`) — 다른 사용자 데이터 노출 방지
- **`@Async` + SecurityContext 함정 (6 주차 회수)** — `SecurityContextHolder` 도 ThreadLocal 기반. 컨트롤러 안에서 `@Async` 메서드 부르면 새 스레드는 SecurityContext 비어있음 → 권한 예외. 해결: `DelegatingSecurityContextExecutor` 사용 또는 `SecurityContextHolder.setStrategyName(MODE_INHERITABLETHREADLOCAL)`
- **Refresh 토큰 회전 (RTR) 의 동시성 문제** — 모바일 / SPA 가 access 만료 시점에 N 개 API 동시 호출 → 동시 N 개 refresh 요청 → 한 개만 갱신 성공 / 나머지는 "옛 refresh 재사용 = 도난" 으로 판단 → 강제 로그아웃 (억울한 장애). 해결: 프론트 단 갱신 락 / 백엔드 단 짧은 grace period (예: 5 초간 옛 refresh 도 허용)


━━━━━━━━━━━━━━━━━━━━━━━━━━
막힐 때
━━━━━━━━━━━━━━━━━━━━━━━━━━

**비밀번호 비교가 항상 실패함**:
1. BCrypt 는 같은 평문도 매번 다른 해시 — `equals` 비교 X. `passwordEncoder.matches(raw, hashed)`
2. DB 에 해시가 잘려서 저장되지 않았나 (varchar 길이 60+)
3. 해시 알고리즘이 일관된가 — BCryptPasswordEncoder 한 가지로

**`@PreAuthorize` 가 동작 안 함**:
1. `@EnableMethodSecurity` 활성화했는가
2. self-invocation 인가 — 같은 클래스 `this.method()` 호출 (5, 6, 7, 9 주차 함정)
3. 메서드가 `public` 인가 (AOP 프록시 한계)

**JWT 서명 검증 실패**:
1. 키가 같은가 — 발급 / 검증 양쪽 동일 SecretKey
2. 토큰 만료 확인
3. `alg` 가 라이브러리 허용 알고리즘인가

**세션이 다중 인스턴스에서 안 공유됨**:
1. `@EnableRedisHttpSession` 활성화
2. Redis 호스트 / 포트 정확
3. 두 인스턴스가 같은 Redis 보고 있는가

**CORS 에러**:
1. Spring Security 의 CORS 설정 필요 (`http.cors(...)`)
2. 브라우저 preflight (OPTIONS) 도 허용
3. 인증 헤더는 `allowCredentials=true` + origin 명시 (와일드카드 X)
