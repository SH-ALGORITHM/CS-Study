# 11주차 예시 코드 — BCrypt + JWT + @PreAuthorize

> ⚠️ 베끼지 말고 본인 도메인으로.

## 10 주차와 다른 점

| | 10 주차 HTTP / Pool | 11 주차 Auth |
|---|---|---|
| 문제 | 외부 호출 부하 제어 | 매 요청 신원 확인 |
| 도구 | Pool / Timeout / CB | BCrypt / Session / JWT / @PreAuthorize |
| 회수 | 7 OSIV / 9 캐시 | 5 AOP / 6 Event / 9 Redis / 10 외부 호출 |

## 폴더 구조

```
example/
├── README.md
├── build.gradle               Spring Security + JPA + jjwt
└── src/main/
    ├── resources/application.properties
    └── java/
        ├── infra/MeasurementLog.java
        ├── jwt/JwtService.java       jjwt 0.12+ 발급 + 검증
        └── stage/
            ├── s1/  BCrypt
            │   └── Stage1_1_BCrypt
            ├── s2/  JWT (발급 + 함정)
            │   ├── Stage2_1_JwtCreateVerify
            │   └── Stage2_2_JwtTraps (alg=none / 만료 / 서명 누락)  ★
            └── s3/  @PreAuthorize
                ├── Stage3_1_PreAuthorize
                └── Stage3_2_SelfInvocation (5,6,7,9 주차 회수)  ★
```

## example 범위 — 시나리오와 차이

| 빠진 부분 | 이유 |
|---|---|
| **세션 / Redis 세션 (STAGE 2-1, 2-2)** | 풀 웹 부팅 + 브라우저 쿠키 (`JSESSIONID`) 관찰 필요. main 단순 실행과 안 맞음. 시나리오 본문 + 본인 도메인에서 |
| **OAuth 2.0 (STAGE 4)** | Google / Kakao 클라이언트 ID 필요. 학습자 부담 절약 |

→ `build.gradle` 의도된 차이:
- **scenario** — 세션 + Redis + OAuth 까지 풀 구성
- **example** — BCrypt + JWT + @PreAuthorize 만 (오프라인 실행 가능)

## 실행 방법

```bash
cd topics/11-auth/example

# STAGE 1 — BCrypt (부팅 X, 단순 main)
./gradlew run -PmainClass=stage.s1.Stage1_1_BCrypt

# STAGE 2 — JWT
./gradlew run -PmainClass=stage.s2.Stage2_1_JwtCreateVerify
./gradlew run -PmainClass=stage.s2.Stage2_2_JwtTraps         # ★ 면접 직결

# STAGE 3 — @PreAuthorize + AOP 회수
./gradlew run -PmainClass=stage.s3.Stage3_1_PreAuthorize
./gradlew run -PmainClass=stage.s3.Stage3_2_SelfInvocation   # ★ 5,6,7,9 회수
```

## 핵심 학습

1. **STAGE 1** — BCrypt salt + cost factor 직접 관찰
2. **STAGE 2** ★ — JWT 구조 + 4 함정 (alg=none / 만료 / 서명 / Bearer 평문) 직접 시연
3. **STAGE 3** ★ — `@PreAuthorize` AOP + **self-invocation (5, 6, 7, 9 주차 회수)**
