# 4주차 예시 코드 — 알림 발송 도메인 (Spring IoC / DI / Bean)

scenario.md 의 10 개 도메인과 **별개로** 만든 참고 코드입니다.
3 주차의 `BankAccount` 가 락 3 종 비교였다면, **이번엔 같은 다형성 도메인을 IoC 컨테이너로 푸는 버전**.

> ⚠️ **베끼지 마시고 본인 도메인으로 변환하세요.** "이런 식으로 흘러가는구나" 감을 잡는 용도.

## 3 주차와 무엇이 같고 다른가

| | 3 주차 BankAccount | 4 주차 Notification |
|---|---|---|
| 풀려고 하는 문제 | 동시성 / race | 객체 생성 / 의존성 연결 |
| 도구 | `SELECT FOR UPDATE` / version / Redis SETNX | Spring `@Component` / `@Bean` / `@Autowired` / `@Qualifier` |
| 학습 포인트 | 락 도구별 trade-off + 데드락 | Bean 라이프사이클 + DI 방식별 trade-off + 순환 참조 |
| 객체 생성 | 본인이 직접 `new` | 컨테이너가 대신 |
| 자원 해제 | `close()` 직접 호출 | `@PreDestroy` / `destroyMethod="close"` |

핵심: 3 주차에서 직접 짠 `DataSourceFactory` 싱글톤이 4 주차에서 `@Bean(destroyMethod="close")` 한 줄로 대체됨 — STAGE 2-1 의 마이그레이션 학습.

## 폴더 구조

```
example/
├── README.md                              # 지금 이 파일
├── build.gradle                           # Spring Boot 3.x + HikariCP + PostgreSQL JDBC
└── src/main/
    ├── java/
    │   ├── stage/                              # 학습자가 ▶ 누르는 main (시나리오 STAGE 번호별)
    │   │   ├── s1/                             # STAGE 1: 손 관찰
    │   │   │   ├── Stage1Lifecycle.java        # @PostConstruct / @PreDestroy 순서
    │   │   │   ├── Stage1ScanVsBean.java       # @ComponentScan vs @Bean
    │   │   │   ├── Stage1GetBeanVsAutowired.java   # getBean() vs @Autowired
    │   │   │   └── Stage1BootCount.java        # @SpringBootApplication Bean 수
    │   │   ├── s2/                             # STAGE 2: 도메인 코드
    │   │   │   ├── Stage2Migration.java        # DataSourceFactory → @Bean
    │   │   │   ├── Stage2InjectionTypes.java   # 생성자 / 필드 / 세터 비교
    │   │   │   ├── Stage2Qualifier.java        # @Qualifier 명시 주입
    │   │   │   ├── Stage2PrimaryConflict.java  # @Primary vs @Qualifier
    │   │   │   └── Stage2MapInjection.java     # Map<String, Sender> 자동 주입
    │   │   ├── s3/                             # STAGE 3: 측정
    │   │   │   ├── Stage3_A_Pure.java          # 순수 main() 부팅 (별도 JVM)
    │   │   │   ├── Stage3_B_Spring.java        # AnnotationConfigContext 부팅
    │   │   │   ├── Stage3_C_Boot.java          # SpringApplication.run() 부팅
    │   │   │   ├── Stage3Scope.java            # 싱글톤 vs 프로토타입 카운트
    │   │   │   └── Stage3Lazy.java             # @Lazy 전후 부팅 시간
    │   │   ├── s4/                             # STAGE 4: 순환 참조
    │   │   │   ├── Stage4Circular.java         # 생성자 / 필드 / 세터 순환 참조
    │   │   │   └── Stage4Resolve.java          # @Lazy / 설계 재검토 해결
    │   │   └── s5/                             # STAGE 5 (보너스): AOP 브릿지
    │   │       └── Stage5ProxyBeanMethods.java # @Configuration(proxyBeanMethods) true vs false
    │   ├── domain/
    │   │   ├── NotificationSender.java    # 인터페이스
    │   │   ├── EmailSender.java           # @Component("email") + @Primary
    │   │   ├── SmsSender.java             # @Component("sms")
    │   │   ├── PushSender.java            # @Component("push")
    │   │   ├── SlackSender.java           # @Component("slack")
    │   │   └── NotificationService.java   # @Service, 생성자 주입
    │   └── infra/                         # 측정 로그 + 마이그레이션 학습용 DataSource
    │       ├── MeasurementLog.java        # 1~3 주차와 동일 패턴 (자동 누적)
    │       └── (Stage2Migration 학습 시 DataSourceConfig 직접 작성)
    └── resources/
        └── (필요 시 application.properties)
```

## 실행 전

```bash
# 의존성 다운로드
./gradlew build

# (STAGE 2-1 마이그레이션 학습용만) DB 띄우기
docker compose up -d
```

> STAGE 1, 3, 4 는 DB 없이도 실행 가능. STAGE 2-1 만 PostgreSQL 필요.

## STAGE 별 실행

| 파일 | 단계 | 보는 것 |
|---|---|---|
| `stage.s1.Stage1Lifecycle` | s1-1 | Bean 생성자 → @PostConstruct → @PreDestroy 순서 |
| `stage.s1.Stage1ScanVsBean` | s1-2 | @ComponentScan vs @Bean 직접 등록 차이 |
| `stage.s1.Stage1GetBeanVsAutowired` | s1-3 | Service Locator vs DI |
| `stage.s1.Stage1BootCount` | s1-4 | @SpringBootApplication 의 자동 등록 Bean 100~200 개 확인 |
| `stage.s2.Stage2Migration` | s2-1 | DataSourceFactory 싱글톤 → @Configuration + @Bean |
| `stage.s2.Stage2InjectionTypes` | s2-3 | 생성자 / 필드 / 세터 — final 가능 여부 / 테스트 용이성 |
| `stage.s2.Stage2Qualifier` | s2-4 | @Qualifier("email") 로 4 개 중 1 개 지정 |
| `stage.s2.Stage2PrimaryConflict` | s2-5 | @Primary 와 @Qualifier 동시 → @Qualifier 가 이김 |
| `stage.s2.Stage2MapInjection` | s2-4 | Map<String, NotificationSender> 로 4 개 모두 받기 |
| `stage.s3.Stage3_A/B/C` | s3-1 | 별도 JVM 5 회 실행 → 부팅 시간 평균 (측정값은 컨테이너 초기화만 — JVM 기동은 `nanoTime` 측정 밖) |
| `stage.s3.Stage3Scope` | s3-3 | 싱글톤 1000 회 호출 = 생성자 1 회 / 프로토타입 1000 회 |
| `stage.s3.Stage3Lazy` | s3-4 | 무거운 Bean (sleep 2 초) 에 @Lazy 적용 전후 |
| `stage.s4.Stage4Circular` | s4-1~2 | A ↔ B 순환 참조 — 생성자 (부팅 실패) vs 필드 / 세터 |
| `stage.s4.Stage4Resolve` | s4-3 | @Lazy 적용 / 설계 재검토 해결 |
| `stage.s5.Stage5ProxyBeanMethods` | s5 | @Configuration(proxyBeanMethods) true (CGLIB 프록시) vs false (Lite). 5 주차 AOP 브릿지 |

실행 명령:
```bash
./gradlew run -PmainClass=stage.s1.Stage1Lifecycle
./gradlew run -PmainClass=stage.s3.Stage3_A_Pure
# ...
```

각 파일 main 옆 ▶ 클릭해도 됨. 콘솔 + `measurements.md` 자동 누적.

## 본인 도메인으로 변환할 때

| 알림 발송 | → | 본인 도메인 (예: 결제 PG) |
|---|---|---|
| `NotificationSender` | → | `PaymentGateway` |
| `EmailSender` / `SmsSender` / ... | → | `TossPayment` / `KakaoPayment` / `NaverPayment` |
| `@Qualifier("email")` | → | `@Qualifier("toss")` |
| `NotificationService.notify(to, msg)` | → | `PaymentService.pay(userId, amount)` |

**구조는 같음, 인터페이스 / 구현체 이름만 달라짐.**

## STAGE 1 (순수 Spring 컨테이너 손으로) 안내

STAGE 1 은 Spring Boot 마법 빼고 `AnnotationConfigApplicationContext` 를 직접 `new` 해서 컨테이너를 손으로 만지는 단계.

시나리오 `topics/04-ioc-bean/scenario.md` 의 **STAGE 1** 섹션 참고.

## 주의사항

- 본인 폴더 (`members/{본인이름}/`) 에 본인 도메인 코드 작성 — 여기 example 은 참고용
- 측정 코드 중간에 과도한 `println` X — 부팅 시간 측정값 왜곡
- `MeasurementLog.save()` 는 측정 끝난 후 한 번만
- **STAGE 3-1 부팅 시간** 은 같은 JVM 순차 측정 X — JIT 웜업 효과로 뒤쪽이 빨라 보임. 별도 main 5 회 실행 후 평균
- AI 에게 "이 코드 짜줘" 금지 — 본인이 시도 후 힌트 받기 (`CLAUDE.md` 룰)
- Spring Boot 3.x = javax → **jakarta** 패키지. `jakarta.annotation.PostConstruct` 사용 (`javax` 아님)
- `@Component` + `@Bean` 같은 클래스 중복 등록 X — `ConflictingBeanDefinitionException`
