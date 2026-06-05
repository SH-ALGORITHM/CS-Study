# 4주차 — 객체를 직접 만들지 말고 컨테이너에 맡기자 (Spring IoC / DI / Bean)

이번 주제: 1~3 주차 내내 직접 `new` 한 `DataSourceFactory`, `RedisClientFactory`, `HikariCP` 풀, 그리고 도메인 객체 — 모두 본인이 손으로 생성하고 `close()` 까지 챙겼다. **객체 생성과 의존성 연결을 컨테이너가 대신 해주면** 코드가 어떻게 바뀌고, 그 대가로 무엇을 잃는지 측정한다.

5 가지 학습 축:
- IoC 컨테이너 (`AnnotationConfigApplicationContext`) — 컨테이너를 손으로 만들고 Bean 꺼내기
- Bean 라이프사이클 (`@PostConstruct`, `@PreDestroy`) — 언제 생성 / 주입 / 소멸되는지
- 의존성 주입 (생성자 / 필드 / 세터) — 각 방식의 차이 + 순환 참조 감지 시점
- 다형성 활용 (`@Qualifier`, `@Primary`) — Strategy 패턴이 IoC 와 만나면
- 순환 참조 (Spring Boot 2.6+) — 일부러 만들고 부팅 실패 → 해결 3 가지

---

## 우선 알아둬야 할 단어 (시작 전 1 분)

| 단어 | 풀어쓰면 |
|---|---|
| **IoC (Inversion of Control)** | 객체 생성 / 의존성 연결의 주체를 본인 코드 → 프레임워크 (컨테이너) 로 뒤집는 패턴 |
| **DI (Dependency Injection)** | IoC 의 구체 구현. 컨테이너가 객체에 필요한 의존성을 "주입"해주는 방식 (생성자 / 세터 / 필드) |
| **Bean** | 스프링 컨테이너가 관리하는 객체. 일반 객체와 구분되는 점 = "라이프사이클을 컨테이너가 책임짐" |
| **ApplicationContext** | 스프링의 IoC 컨테이너 본체. 모든 Bean 의 등록 / 생성 / 주입 / 소멸을 관리 |
| **BeanDefinition** | Bean 의 메타데이터 (클래스 / 스코프 / 의존성 / 라이프사이클 콜백). 실제 객체가 아닌 "설계도" |
| **싱글톤 (Singleton)** | 스프링 기본 스코프. 컨테이너당 1 개만 생성 → 모든 곳에서 같은 인스턴스 |
| **프로토타입 (Prototype)** | 요청할 때마다 새 인스턴스. 라이프사이클은 생성까지만, 소멸은 컨테이너 책임 X |
| **Bean 스코프 5 가지** | Singleton (기본) / Prototype / **Request** (HTTP 요청 단위) / **Session** (HTTP 세션 단위) / **Application** (서블릿 컨텍스트 1 개) — 뒤 3 개는 웹 환경에서만 |
| **SOLID — DIP (의존성 역전 원칙)** | 구체 클래스 X, 추상 (인터페이스) 에 의존. **DI 가 DIP 를 자동화** — `NotificationService` 가 4 개 구현체 모르고 `NotificationSender` 에만 의존 |
| **`@Component` 계열** | `@Component` / `@Service` / `@Repository` / `@Controller` — 컴포넌트 스캔으로 자동 Bean 등록 |
| **`@Configuration` + `@Bean`** | 코드로 직접 Bean 등록. 외부 라이브러리 객체 (DataSource / RedisClient) 등록에 필수 |
| **`@Autowired`** | 타입 기반 의존성 주입. 같은 타입 여러 개면 `@Qualifier` / `@Primary` 로 구분 |
| **`@Qualifier`** | 같은 타입 Bean 여러 개일 때 이름으로 명시 지정 |
| **`@Primary`** | 같은 타입 Bean 여러 개일 때 "기본값" 지정. `@Qualifier` 가 더 우선 |
| **순환 참조 (Circular Reference)** | A → B → A 의존. Spring Boot 2.6+ 부터 부팅 시점에 막음 |
| **컴포넌트 스캔** | `@ComponentScan` 의 basePackage 부터 모든 `@Component` 계열 클래스 자동 등록 |

> 📚 더 깊은 용어 (라이프사이클 / 스코프 5 가지 / SOLID / 프록시 / CGLIB 등) — [`terms.md`](terms.md) 참고. 3 주차 락 용어 정리와 같은 형식, 10 카테고리 ~ 80 개 용어.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 0. 개념 숙지 (도메인 무관, 모두 동일)
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 필수 개념 (AI 와 대화하며 숙지)

### IoC 컨테이너의 본질
1. **객체 생성을 컨테이너에 맡기는 이유** — 1~3 주차에서 직접 짠 `DataSourceFactory` 가 어떤 문제를 가졌는지 (싱글톤 보장 / 생명주기 관리 / 의존성 그래프 추적) 본인 말로
2. **`new Service(new Repository(new DataSource()))`** 같은 수동 의존성 연결과 DI 의 차이. 의존성이 5 개 6 개 늘어나면 어떻게 되는가
3. **`AnnotationConfigApplicationContext` vs `SpringApplication.run()`** 차이 — 후자는 어떤 마법을 추가로 해주는가

### Bean 라이프사이클
4. **생성 → 의존성 주입 → `@PostConstruct` → 사용 → `@PreDestroy` → 소멸** 순서 + 각 단계에서 본인이 개입 가능한 지점
5. **싱글톤 vs 프로토타입** — 언제 생성되는가 + 컨테이너 종료 시 누구의 `@PreDestroy` 가 호출되는가 (프로토타입은 호출 안 됨)

### 의존성 주입 방식
6. **생성자 / 필드 / 세터 주입** — 각 방식의 코드 모양 + final 가능 여부 + 테스트 용이성 + **순환 참조 감지 시점**
7. **생성자 주입이 권장되는 이유 3 가지** — 불변성 (final) / 필수 의존성 명시 / 순환 참조 부팅 시점 감지

### 다형성과 DI
8. **같은 타입 Bean 이 여러 개일 때 `@Autowired` 동작** — `NoUniqueBeanDefinitionException` 발생 → `@Qualifier` / `@Primary` 로 해결
9. **`@Qualifier` vs `@Primary` 우선순위** — 둘 다 있으면 어느 쪽이 이기는가 (`@Qualifier`)

### 순환 참조
10. **Spring Boot 2.6+ 의 기본 동작** — `spring.main.allow-circular-references=false` (기본값) → 부팅 실패. 왜 막았는가
11. **주입 방식별 감지 시점** — 생성자 (부팅 시점, BeanCurrentlyInCreationException) vs 필드 / 세터 (런타임에 NullPointer 또는 무한 루프 가능)
12. **해결 3 가지** — (a) 설계 재검토 (가장 권장) (b) 한쪽 `@Lazy` (c) 세터 / 필드 주입으로 변경. 트레이드오프

## 자기 검증 (입으로 답할 수 있어야 STAGE 1 시작)

**★ 관문 — 이 3 개는 입으로 답해야 STAGE 1 진입**
- [ ] ★ IoC 가 무엇을 "뒤집는가" — 1 분 본인 말로
- [ ] ★ 생성자 주입이 권장되는 이유 3 가지
- [ ] ★ Spring Boot 2.6+ 가 순환 참조를 막은 이유 + 해결 방법 1 개

**보너스 — STAGE 1 진행하면서 답할 수 있게**
- [ ] `@Component` 와 `@Bean` 차이 — 언제 어느 쪽?
- [ ] 싱글톤 Bean 이 멀티스레드 환경에서 안전한가 (조건은?)
- [ ] `@Qualifier` 와 `@Primary` 충돌 시 어느 쪽이 이기는가
- [ ] `@PostConstruct` 가 생성자가 아닌 별도 메서드인 이유
- [ ] 3 주차의 `DataSourceFactory` 싱글톤 패턴이 스프링의 싱글톤 Bean 과 같은가 다른가


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 1. 본인 도메인 선택 — 4 주차에 맞게 (의존성 풍부 + 다형성 활용 가능)
━━━━━━━━━━━━━━━━━━━━━━━━━━

4 주차 학습 포인트 (**Bean / DI / 다형성 / 라이프사이클 / 순환 참조**) 는 **의존성이 풍부하고 다중 구현체가 자연스러운 도메인** 에서 잘 드러난다. 3 주차에 단일 도메인 (계좌 / 쿠폰 / 좌석) 했던 사람은 새 도메인 고려.

## 옵션 — 3 주차 도메인 계속 쓰기 vs 새 도메인

| 옵션 | 권장 대상 | 흐름 |
|---|---|---|
| **A. 3 주차 도메인 계속 + 다형성 사이드** | 도메인 새로 짜기 부담스러운 사람 | STAGE 2-1 ~ 2-3 까지 본인 3 주차 도메인 (계좌 / P2P / 쿠폰 등) 으로. STAGE 2-4 ~ 2-5 (다형성) 는 알림 발송 같은 **사이드 모듈을 끼워넣어** 학습 |
| **B. 새 도메인 선택** | 다형성 학습 본격 + 새 설계 부담 OK | STEP 1 후보표에서 다형성 ★★★ 도메인 (알림 / 파일저장 / 결제PG 등) 선택 |
| **C. 혼합** | 가장 무난 | STAGE 2-1 (3 주차 `DataSourceFactory` → `@Bean` 마이그레이션) 만 공통. 2-2 부터 본인 선택 |

**모두 STAGE 2-1 의 3 주차 브릿지 (DataSourceFactory → @Bean) 는 공통.** 3 주차 코드가 없는 사람 (스터디 중간 합류) 은 STEP 2 의 공통 예제 사용.

## 후보 도메인 + 적합도 (12 개 — 7 명이 1 개씩 + 여유 5)

| # | 도메인 | 의존성 풍부 | 다형성 자연 | 순환 참조 만들기 | 메모 |
|---|---|---|---|---|---|
| 1 | **알림 발송** (`notification`) | ★★ | ★★★ | ★★ | Email / SMS / Push / Slack 4 가지 구현체. `@Qualifier` / `@Primary` 학습 가장 강함 |
| 2 | **파일 저장** (`file_storage`) | ★★ | ★★★ | ★ | Local / S3 / GCS / Azure Blob 다중 구현체. 환경별 빈 교체 자연스러움 |
| 3 | **결제 PG 연동** (`payment_pg`) | ★★★ | ★★★ | ★★ | Toss / Kakao / NaverPay 다중 PG. `PaymentGateway` 인터페이스 + 다중 구현 |
| 4 | **인증 전략** (`auth_provider`) | ★★★ | ★★★ | ★★ | OAuth (Google / Kakao / GitHub) + JWT + Session 다중 인증. Strategy 패턴 |
| 5 | **검색 엔진** (`search_engine`) | ★★ | ★★★ | ★ | DB LIKE / Elasticsearch / Lucene / Algolia. 환경별 구현 교체 |
| 6 | **캐시 Provider** (`cache_provider`) | ★★ | ★★★ | ★ | Caffeine (로컬) / Redis / Memcached 다중 구현. 3 주차 Redis 연결 |
| 7 | **이커머스 주문** (`order`) | ★★★ | ★★ | ★★★ | OrderService → PaymentService → InventoryService → NotificationService 계층 깊음. 순환 참조 학습 풍부 |
| 8 | **게시판** (`board`) | ★★ | ★ | ★★ | User / Post / Comment / Like Service. 가장 단순, 입문자용 |
| 9 | **도서 대여** (`library`) | ★★ | ★ | ★★ | Book / Member / Rental / Reservation. 계층 분리 학습 |
| 10 | **메시지 큐 핸들러** (`message_handler`) | ★★ | ★★★ | ★ | Topic 별 Handler 다중 구현. `Map<String, Handler>` 자동 주입 학습 |
| 11 | **할인 정책** (`discount_policy`) | ★★ | ★★★ | ★ | Strategy 패턴 정석 — 정률 / 정액 / 등급 / 쿠폰 / 첫 구매. **OCP / DIP 학습 최고** |
| 12 | **배송 업체** (`shipping`) | ★★ | ★★★ | ★ | CJ / 한진 / 우체국 / 로젠. 결제 PG 와 한 세트 (결제 → 배송 흐름) |

> **다형성 ★★★ 조건** = 같은 인터페이스의 구현체가 자연스럽게 2 개 이상 + 환경 / 요청별로 교체 가능. STAGE 2-4 (`@Qualifier`) / 2-5 (`@Primary`) 학습이 강하게 작동.

## 도메인별 예상 시나리오 한 줄

| # | 한 줄 시나리오 |
|---|---|
| 1 | `NotificationService` 가 `NotificationSender` 인터페이스 주입. 구현체 4 개 중 `@Qualifier("email")` 로 지정. `@Primary` 는 Email 에 |
| 2 | `FileStorageService` 가 `StorageProvider` 주입. 로컬 개발 = `LocalStorage`, 운영 = `S3Storage` 를 `@Profile` 로 분기 |
| 3 | `PaymentService` 가 결제 요청 시 `Map<String, PaymentGateway>` 받아서 사용자가 선택한 PG 로 분기 |
| 4 | `AuthService` 가 `AuthProvider` 주입. provider 종류 따라 OAuth / JWT / Session 분기 |
| 5 | `ProductSearchService` 가 `SearchEngine` 주입. 로컬 개발 = DB LIKE, 운영 = Elasticsearch |
| 6 | `CacheService` 가 `Cache` 인터페이스 주입. 단건 = Caffeine, 분산 = Redis |
| 7 | `OrderService` → `PaymentService` + `InventoryService` + `NotificationService` 모두 의존. 4 계층 그래프 |
| 8 | `PostService` → `UserService` + `CommentService`. `CommentService` → `PostService` 일부러 만들면 순환 참조 재현 |
| 9 | `RentalService` → `BookService` + `MemberService` + `NotificationService` |
| 10 | `MessageDispatcher` 가 `Map<String, MessageHandler>` 받아서 topic 으로 분기. handler 추가 = 새 `@Component` 만 추가 |
| 11 | 주문 시 정률 (10%) / 정액 (1000 원) / 등급 (VIP 15%) / 쿠폰 / 첫 구매 중 정책 선택. `@Qualifier` 또는 `Map<String, DiscountPolicy>` 로 분기. 새 정책 추가 = 클래스 1 개 (OCP) |
| 12 | 결제 완료 후 배송 업체 선택 (CJ / 한진 / 우체국 / 로젠). `ShippingProvider` 인터페이스 + 4 구현체. 배송비 계산 / 운송장 조회 분기 |

## 학습자 프로필별 추천

| 본인 상황 | 추천 도메인 |
|---|---|
| 3 주차 P2P / 계좌 / 주식 그대로 쓰고 다형성만 추가 | **옵션 A** — 본인 도메인 + 알림 발송 사이드 모듈 |
| 다형성 / Strategy 패턴 본격 학습 | **1 알림** / **3 결제 PG** / **11 할인 정책** / **12 배송 업체** — `@Qualifier` / `@Primary` 학습 가장 강함 |
| OCP / DIP 면접 답변 강화 | **11 할인 정책** — Strategy 패턴 정석. "새 정책 추가 시 코드 수정 없음" 시연 가장 명확 |
| 결제 흐름 한 세트로 학습 | **3 결제 PG** + **11 할인** + **12 배송** — 주문→할인→결제→배송 한 흐름 |
| 순환 참조 깊이 학습 | **7 이커머스 주문** — 4 계층 의존성, 순환 참조 자연스럽게 만들 수 있음 |
| 입문자 / Spring 처음 | **8 게시판** — 가장 단순한 계층 (User/Post/Comment) |
| 5 주차 AOP 까지 자연스러운 브릿지 | **3 결제 PG** 또는 **1 알림** — "모든 외부 호출에 로깅" AOP 가 5 주차로 연결 |
| 3 주차 코드 그대로 쓰기 (시간 부족) | **옵션 C** — STAGE 2-1 마이그레이션만, 그 후 본인 도메인 그대로 |

## 3 주차 INSERT 도메인이 약한 이유

3 주차에 단일 row 도메인 (8 콘서트 좌석 / 9 포인트) 했던 사람은:
- 의존성이 1~2 개라 DI 학습 포인트가 약함
- 다중 구현체 자연스럽지 않음 → `@Qualifier` 학습 비어 보임
- 순환 참조 만들기 어려움

→ 단일 row 도메인 출신은 **1 알림** / **3 결제 PG** / **7 이커머스** 중 추천.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 2. 클래스 구조 매핑
━━━━━━━━━━━━━━━━━━━━━━━━━━

도메인별 추천 클래스 구조. **본인 도메인 살릴 사람은 STAGE 2-1 (DataSourceFactory → @Bean) 마이그레이션 후 자유롭게 확장.**

| 도메인 | 인터페이스 | 구현체 | 상위 Service |
|---|---|---|---|
| 1 알림 발송 | `NotificationSender` | `EmailSender` / `SmsSender` / `PushSender` / `SlackSender` | `NotificationService` |
| 2 파일 저장 | `StorageProvider` | `LocalStorage` / `S3Storage` / `GcsStorage` | `FileStorageService` |
| 3 결제 PG | `PaymentGateway` | `TossPayment` / `KakaoPayment` / `NaverPayment` | `PaymentService` |
| 4 인증 전략 | `AuthProvider` | `GoogleOAuth` / `KakaoOAuth` / `JwtAuth` / `SessionAuth` | `AuthService` |
| 5 검색 엔진 | `SearchEngine` | `DbLikeSearch` / `ElasticsearchSearch` / `LuceneSearch` | `ProductSearchService` |
| 6 캐시 Provider | `Cache<K,V>` | `CaffeineCache` / `RedisCache` / `MemcachedCache` | `CacheService` |
| 7 이커머스 주문 | (단일 구현체 위주) | `OrderService` / `PaymentService` / `InventoryService` / `NotificationService` | `OrderFacade` |
| 8 게시판 | (단일 구현체 위주) | `UserService` / `PostService` / `CommentService` / `LikeService` | `PostFacade` |
| 9 도서 대여 | `NotificationSender` (반납 알림용) | `EmailSender` / `SmsSender` | `RentalService` / `BookService` / `MemberService` |
| 10 메시지 큐 | `MessageHandler` | `OrderEventHandler` / `PaymentEventHandler` / `UserEventHandler` | `MessageDispatcher` |
| 11 할인 정책 | `DiscountPolicy` | `PercentDiscount` / `FixedDiscount` / `GradeDiscount` / `CouponDiscount` / `FirstOrderDiscount` | `OrderService` (또는 `PriceCalculator`) |
| 12 배송 업체 | `ShippingProvider` | `CjLogistics` / `HanjinExpress` / `KoreaPost` / `LogenDelivery` | `ShippingService` |

## 공통 — 3 주차 코드 마이그레이션 (모두 동일, STAGE 2-1)

3 주차에 본인이 짠 `DataSourceFactory` / `RedisClientFactory` 싱글톤 패턴을 스프링 `@Configuration + @Bean` 으로 옮긴다:

```java
// Before — 3 주차 DataSourceFactory (직접 싱글톤)
public final class DataSourceFactory {
    private static final HikariDataSource DS;
    static {
        DS = new HikariDataSource();
        DS.setJdbcUrl("jdbc:postgresql://localhost:5433/csstudy");
        DS.setUsername("csstudy");
        DS.setPassword("csstudy1234");
    }
    public static DataSource get() { return DS; }
    public static void shutdown() { DS.close(); }
}

// After — 4 주차 @Configuration
@Configuration
public class DataSourceConfig {
    @Bean(destroyMethod = "close")
    public DataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://localhost:5433/csstudy");
        ds.setUsername("csstudy");
        ds.setPassword("csstudy1234");
        return ds;
    }
}
```

> 핵심: `destroyMethod = "close"` 가 빠지면 컨테이너 종료 시 connection pool 누수. 3 주차의 `shutdown()` 호출이 이 한 줄로 대체된다.

## measurements.md 형식 (1, 2, 3 주차와 일관)

자동 누적 형식 그대로:
```
- [05-26 14:00] s1 · ApplicationContext 손 측정 (Bean 등록 / 라이프사이클)
- [05-27 22:00] s2 · 본인 도메인 IoC 마이그레이션 완료 — 코드 라인 수 Before X → After Y
- [05-28 22:00] s3 · 부팅 시간 — main() Xms / AnnotationConfigContext Yms / SpringApplication.run() Zms
- [05-28 22:30] s3 · Bean 수 — 빈 프로젝트 X / 본인 도메인 Y / @SpringBootApplication Z
- [05-28 23:00] s3 · 싱글톤 vs 프로토타입 — 1000 회 호출, 생성자 카운트 1 vs 1000
- [05-29 22:00] s4 · 순환 참조 재현 — 생성자 (부팅 실패) / 세터 (런타임 통과) / @Lazy (해결)
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
환경
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Java 21
- **Spring Boot 3.x** (Spring 6.x) — STAGE 1 일부는 순수 Spring (`AnnotationConfigApplicationContext`) 도 직접 사용
- 3 주차 코드 마이그레이션 시 PostgreSQL 16 + Redis 7 (`docker-compose.yml` 그대로)
- Bean 측정용 도구: `ApplicationContext.getBeanDefinitionCount()`, `AtomicInteger`, `System.nanoTime()` 또는 `StopWatch`

## build.gradle 추가

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    // 3 주차 도메인 살릴 사람만:
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.postgresql:postgresql'
    implementation 'io.lettuce:lettuce-core:6.3.0.RELEASE'
}
```

> 단, STAGE 1-1 ~ 1-3 (순수 Spring `AnnotationConfigApplicationContext` 학습) 까지는 `spring-context` 만 있어도 됨. STAGE 1-4 부터 Spring Boot.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STAGE 진행 가이드
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 예상 학습 시간 (한 주 기준)

| 단계 | 시간 | 비고 |
|---|---|---|
| STAGE 1 (손 관찰 — Bean 라이프사이클 / 의존성 / 컨테이너) | 2~3 시간 | **화요일까지 (필수)** |
| STAGE 2-1 (3 주차 코드 마이그레이션) | 1~2 시간 | **화요일까지 (필수)** — `DataSourceFactory` → `@Bean` |
| STAGE 2-2 ~ 2-3 (계층 분리 + 생성자 주입) | 2~3 시간 | 본인 도메인 적용 |
| **STAGE 2-4 ~ 2-5 (`@Qualifier` / `@Primary`)** ★ | **2~3 시간** | 다형성 도메인이면 핵심 학습 포인트 |
| STAGE 3 (부팅 시간 / Bean 수 / 싱글톤 측정) | 2~3 시간 | 6 케이스 측정 + 해석 |
| STAGE 4 (순환 참조 + 4 가지 시점 비교) | 2~3 시간 | 면접 직결 |
| **합계** | **11~17 시간** | |
| STAGE 5 보너스 (`proxyBeanMethods`) | 30~60 분 | 여유 시. 5 주차 AOP 브릿지 |

**배분**:
- 직장인 (평일 저녁 2 시간 × 5 + 주말 8 시간) — 충분
- 학생 (주말 풀타임 2 일) — 충분
- 부담스러우면 **STAGE 2-4 ~ 2-5 (다형성) 가 가장 무겁고 면접 가치 높음** — 시간 부족 시 STAGE 3 측정을 짧게

### [화 11:00 — Draft PR 마감 + 겪기 발표] — STAGE 1 + STAGE 2-1

> 4 주차는 **STAGE 2-1 마이그레이션까지 화요일 분량**. STAGE 1 만 하면 라이프사이클 / Bean 수 추상 개념만 잡고 끝 — 발표가 약함. 3 주차 `DataSourceFactory` → `@Bean(destroyMethod="close")` 변환을 끼우면 "스프링이 왜 우아한가" 를 코드 변환으로 직접 체감 가능 (1~2 시간 추가).

#### ▸ STAGE 1 — 순수 Spring 컨테이너 손으로 보기 (필수)

**목표**: `AnnotationConfigApplicationContext` 를 직접 `new` 해서 Bean 이 어떻게 등록 / 생성 / 주입 / 소멸되는지 println 으로 추적.

##### 1-1. Bean 라이프사이클 추적

```java
@Component
public class SampleBean {
    public SampleBean() {
        System.out.println("[1] 생성자 호출");
    }
    @Autowired
    public void setDependency(OtherBean other) {
        System.out.println("[2] 의존성 주입 (세터)");
    }
    @PostConstruct
    public void init() {
        System.out.println("[3] @PostConstruct 호출");
    }
    @PreDestroy
    public void destroy() {
        System.out.println("[4] @PreDestroy 호출");
    }
}

public class Stage1Lifecycle {
    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(AppConfig.class);
        SampleBean bean = ctx.getBean(SampleBean.class);
        ctx.close();   // [4] 호출 확인
    }
}
```

**관찰 포인트**:
- [1] → [2] → [3] 순서 + 각 단계 사이에 본인이 개입 가능한 지점
- `ctx.close()` 호출 시점에 [4] 가 호출되는가
- 프로토타입 스코프로 바꾸면 [4] 가 호출되는가 (호출 안 됨 — 컨테이너 책임 X)

##### 1-2. `@ComponentScan` vs `@Bean` 차이

```java
// 방법 A — 컴포넌트 스캔 (자동)
@Configuration
@ComponentScan("com.example.app")   // 패키지 아래 @Component 자동 등록
public class AppConfig {}

// 방법 B — @Bean 직접 등록 (수동)
@Configuration
public class AppConfig {
    @Bean
    public SampleBean sampleBean() {
        return new SampleBean();
    }
}
```

**관찰 포인트**:
- 외부 라이브러리 객체 (DataSource / RedisClient) 를 `@Component` 로 등록 가능한가 — 불가능 (소스 수정 불가)
- → `@Bean` 의 존재 이유
- 같은 클래스를 두 방식 다 등록하면? (`ConflictingBeanDefinitionException`)

##### 1-3. `ApplicationContext.getBean()` vs `@Autowired` 직접 비교

```java
// 방법 A — getBean() 명시 조회 (Service Locator 패턴)
SampleBean bean = ctx.getBean(SampleBean.class);

// 방법 B — @Autowired 주입 (DI)
@Service
public class UserService {
    private final SampleBean bean;
    public UserService(SampleBean bean) {   // 생성자 주입
        this.bean = bean;
    }
}
```

**관찰 포인트**:
- 방법 A 는 왜 안티패턴 취급받는가 — 컨테이너에 의존, 테스트 어려움
- 방법 B 가 자연스러운 이유 — 의존성이 코드에 명시됨

##### 1-4. `@SpringBootApplication` 의 자동 등록 Bean 수 확인

```java
@SpringBootApplication
public class Stage1BootCount {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(Stage1BootCount.class, args);
        System.out.println("Bean 수: " + ctx.getBeanDefinitionCount());
        // 모든 Bean 이름 출력
        for (String name : ctx.getBeanDefinitionNames()) {
            System.out.println(" - " + name);
        }
    }
}
```

**관찰 포인트**:
- 빈 프로젝트인데도 100~200 개 Bean 이 자동 등록됨 — `@SpringBootApplication` 의 정체
- `@EnableAutoConfiguration` 이 추가한 Bean 들 — DataSource / WebMvc / Jackson 등
- 본인 도메인 추가 후 몇 개 증가하는지

##### 1-5. STAGE 1 결과 정리

`measurements.md` 또는 별도 섹션에:
```
## STAGE 1 — IoC 컨테이너 (직접 관찰)

라이프사이클 순서: (관찰)
프로토타입 @PreDestroy 호출 여부: (관찰)
@Component vs @Bean 차이 1 줄: (본인 정리)
빈 프로젝트 Bean 수 / @SpringBootApplication 추가 후 Bean 수: (관찰)
```


### [목 11:00 — Ready PR 전환 + 코드 발표] — STAGE 2-2 ~ STAGE 4

> STAGE 2-1 (마이그레이션) 은 화요일까지. 목요일까지는 계층 분리 (2-2) / 생성자 주입 (2-3) / 다형성 (2-4 / 2-5) + STAGE 3 측정 + STAGE 4 순환 참조.

#### ▸ STAGE 2 — 본인 도메인을 IoC 로 옮기기 (필수)

##### 2-1. 3 주차 코드 마이그레이션 (모두 공통, **화요일까지**)

위 STEP 2 의 `DataSourceFactory` → `@Configuration + @Bean` 변환 그대로 적용.

추가로 Redis (3 주차 분산락 사용자):
```java
@Configuration
public class RedisConfig {
    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient() {
        return RedisClient.create("redis://localhost:6379");
    }
}
```

> 측정: 마이그레이션 전후 코드 라인 수 비교. 직접 싱글톤 + close 호출 vs `@Bean(destroyMethod="...")`.

##### 2-2. `@Service` + `@Repository` 계층 분리

> 알림 도메인은 DB 안 쓰니까 example/ 에 매핑 없음. **본인 도메인 변환 시 적용.**

본인 도메인의 기존 클래스 (예: 3 주차 `P2PWallet`) 를:

```java
// Repository — DB 접근
@Repository
public class WalletRepository {
    private final DataSource dataSource;
    public WalletRepository(DataSource ds) {
        this.dataSource = ds;
    }
    public BigDecimal findBalance(long id) { /* JDBC */ }
    public void updateBalance(long id, BigDecimal balance) { /* JDBC */ }
}

// Service — 비즈니스 로직
@Service
public class TransferService {
    private final WalletRepository walletRepo;
    public TransferService(WalletRepository walletRepo) {
        this.walletRepo = walletRepo;
    }
    public void transfer(long from, long to, BigDecimal amount) { /* ... */ }
}
```

> 핵심: 도메인 분리 후 본인이 직접 객체 생성하지 않음. 컨테이너가 `WalletRepository` → `TransferService` 순서로 주입.

##### 2-3. 생성자 vs 필드 vs 세터 주입 비교

3 가지 모두 짜보고 비교:

```java
// A. 생성자 주입 (권장)
@Service
public class A {
    private final B b;
    public A(B b) { this.b = b; }
}

// B. 필드 주입 (테스트 어려움 / final 불가)
@Service
public class C {
    @Autowired
    private B b;
}

// C. 세터 주입 (선택적 의존성)
@Service
public class D {
    private B b;
    @Autowired
    public void setB(B b) { this.b = b; }
}
```

**비교 포인트**:
- `final` 가능 여부 — 생성자만 가능
- 테스트 시 주입 방법 — 생성자만 단순 (`new A(mockB)`)
- 순환 참조 감지 시점 — 생성자만 부팅 시점

##### 2-4. 다중 구현체 + `@Qualifier` (Strategy 패턴)

**도메인이 다형성 ★★★** (알림 / 결제 PG 등) 인 사람만. 본인 도메인 다형성 약하면 알림 발송 사이드 모듈 추가:

```java
public interface NotificationSender {
    void send(String to, String message);
}

@Component("email")
public class EmailSender implements NotificationSender { /* ... */ }

@Component("sms")
public class SmsSender implements NotificationSender { /* ... */ }

@Component("push")
public class PushSender implements NotificationSender { /* ... */ }

@Service
public class NotificationService {
    private final NotificationSender sender;
    public NotificationService(@Qualifier("email") NotificationSender sender) {
        this.sender = sender;
    }
}
```

**또는 `Map<String, NotificationSender>` 로 모두 받기**:
```java
@Service
public class NotificationService {
    private final Map<String, NotificationSender> senders;
    public NotificationService(Map<String, NotificationSender> senders) {
        this.senders = senders;   // {"email": ..., "sms": ..., "push": ...}
    }
    public void send(String channel, String to, String message) {
        senders.get(channel).send(to, message);
    }
}
```

**관찰 포인트**:
- Map 의 **키 = Bean 이름** (기본은 클래스명 camelCase). `@Component("email")` 로 명시하면 키가 `"email"`, 그냥 `@Component` 만 쓰면 키가 `"emailSender"` 가 됨
- `ctx.getBeanDefinitionNames()` 로 실제 Bean 이름 확인 후 `senders.get(...)` 키 맞추기. 안 맞으면 null 반환

##### 2-5. `@Primary` vs `@Qualifier` 우선순위 충돌

일부러 둘 다 붙여서 어느 쪽이 이기는지 확인:

```java
@Component
@Primary
public class EmailSender implements NotificationSender { /* ... */ }

@Component
public class SmsSender implements NotificationSender { /* ... */ }

@Service
public class NotificationService {
    public NotificationService(@Qualifier("smsSender") NotificationSender sender) {
        // @Primary 가 EmailSender 에 있지만, @Qualifier 가 더 우선 → SmsSender 주입됨
    }
}
```

**관찰**:
- `@Qualifier` 가 `@Primary` 보다 우선
- `@Primary` 는 "기본값" 정도의 의미. 명시 지정 (`@Qualifier`) 이 있으면 그쪽이 이김


#### ▸ STAGE 3 — 정량 측정 (필수)

##### 3-1. 부팅 시간 측정 (3 가지 방식 비교)

> ⚠️ **JVM 웜업 주의**: 같은 JVM 에서 3 가지를 순차 측정하면 JIT 웜업 효과로 뒤쪽이 빨라 보일 수 있음. 정확한 비교는 **각 방식을 별도 main 클래스로 실행** 후 결과 모으기. 아래 코드는 한 파일 안에 모은 학습 편의용.

```java
// 방식 A — 순수 main() (Spring 안 씀)
long t1 = System.nanoTime();
WalletRepository repo = new WalletRepository(dataSource);
TransferService svc = new TransferService(repo);
System.out.println("순수: " + (System.nanoTime() - t1) / 1_000_000 + "ms");

// 방식 B — AnnotationConfigApplicationContext (순수 Spring)
long t2 = System.nanoTime();
var ctx = new AnnotationConfigApplicationContext(AppConfig.class);
System.out.println("Spring: " + (System.nanoTime() - t2) / 1_000_000 + "ms");

// 방식 C — SpringApplication.run() (Spring Boot)
long t3 = System.nanoTime();
var bootCtx = SpringApplication.run(MyApp.class, args);
System.out.println("Boot: " + (System.nanoTime() - t3) / 1_000_000 + "ms");
```

> 정확한 측정 원하면 `Stage3_A_Pure.java`, `Stage3_B_Spring.java`, `Stage3_C_Boot.java` 로 별도 실행. 각 JVM 5 회 평균. **측정값은 `nanoTime` 으로 컨테이너 초기화만 감싸므로 JVM 기동 비용은 포함 안 됨.**

##### 3-2. Bean 수 측정

| 케이스 | `getBeanDefinitionCount()` |
|---|---|
| 빈 `@Configuration` | (측정) |
| 본인 도메인 클래스 추가 후 | (측정) |
| `@SpringBootApplication` | (측정) |

##### 3-3. 싱글톤 vs 프로토타입 생성자 호출 카운트

```java
@Component
// @Scope("prototype")   // 주석 토글로 비교
public class CountingBean {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    public CountingBean() {
        System.out.println("생성: " + COUNTER.incrementAndGet());
    }
}

public class Stage3Scope {
    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(AppConfig.class);
        for (int i = 0; i < 1000; i++) {
            ctx.getBean(CountingBean.class);
        }
        // 싱글톤: COUNTER = 1
        // 프로토타입: COUNTER = 1000
    }
}
```

##### 3-4. `@Lazy` 전후 부팅 시간

무거운 Bean (의도적으로 `Thread.sleep(2000)`) 에 `@Lazy` 적용 전후 부팅 시간 비교:

```java
@Component
public class HeavyBean {
    public HeavyBean() {
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
    }
}

// 처음: 부팅 시간 ~2 초 추가
// @Lazy 적용 후: 부팅 시간 정상, 첫 호출 시점에 2 초 지연
```

##### 3-5. 측정 표 (5 회 평균)

| 항목 | 측정값 |
|---|---|
| 순수 main() 부팅 | (ms) |
| `AnnotationConfigContext` 부팅 | (ms) |
| `SpringApplication.run()` 부팅 | (ms) |
| 빈 프로젝트 Bean 수 | (개) |
| 본인 도메인 추가 후 Bean 수 | (개) |
| 싱글톤 1000 회 호출 — 생성자 호출 횟수 | 1 |
| 프로토타입 1000 회 호출 — 생성자 호출 횟수 | 1000 |
| `@Lazy` 전 부팅 시간 | (ms) |
| `@Lazy` 후 부팅 시간 | (ms) |


#### ▸ STAGE 4 — 순환 참조 재현 + 해결 (필수)

##### 4-1. 일부러 순환 참조 만들기

```java
@Service
public class A {
    private final B b;
    public A(B b) { this.b = b; }   // A → B
}

@Service
public class B {
    private final A a;
    public B(A a) { this.a = a; }   // B → A — 순환 참조
}
```

**Spring Boot 2.6+ 결과**:
```
APPLICATION FAILED TO START

The dependencies of some of the beans in the application context form a cycle:
┌─────┐
|  a defined in file [...]
↑     ↓
|  b defined in file [...]
└─────┘
```

##### 4-2. 주입 방식별 감지 시점 비교

같은 순환 참조를 3 가지 방식으로:

| 주입 방식 | 감지 시점 | 메시지 |
|---|---|---|
| 생성자 | 부팅 시점 즉시 | `BeanCurrentlyInCreationException` + 위 ASCII 그래프 |
| 필드 (`@Autowired`) | Spring Boot 2.6+ 부팅 실패 (`allow-circular-references=false`) | 동일 메시지. 옛 버전 (2.5-) 은 런타임에 부분 초기화로 통과 후 NPE 가능 |
| 세터 (`@Autowired`) | 동일 | 동일 |

**왜 주입 방식별로 감지 시점이 다른가** (메커니즘 차이):
- **생성자 주입**: A 만들려면 B 필요, B 만들려면 A 필요 → 둘 다 못 만듦 (닭 · 달걀) → 부팅 즉시 실패
- **필드 / 세터 주입**: A 빈 껍데기 생성 → B 빈 껍데기 생성 → A 에 B 주입 → B 에 A 주입 → 옛 Spring Boot 2.5- 에서는 통과. 단 불완전 초기화 상태로 메서드 호출되면 NPE

> Spring Boot 2.6+ 부터는 모든 주입 방식이 부팅 시점 실패. `spring.main.allow-circular-references=true` 강제 활성화 시에만 옛 동작.

##### 4-3. 해결 3 가지 직접 적용

| 해결책 | 코드 | 트레이드오프 |
|---|---|---|
| **(a) 설계 재검토** | 공통 책임을 제 3 의 서비스로 분리 | 가장 권장 — 근본 해결 |
| **(b) `@Lazy`** | A → B 의존을 `@Lazy` 로 (B 프록시 주입) | 동작은 하지만 설계 냄새 (smell) |
| **(c) 세터 / 필드 주입** | `spring.main.allow-circular-references=true` + 세터로 변경 | 비권장, 옛 코드 호환용 |

##### 4-4. 측정 항목

| 항목 | 의미 |
|---|---|
| 부팅 실패 메시지 | `BeanCurrentlyInCreationException` 캡처 |
| `@Lazy` 적용 후 부팅 성공 여부 | 동작은 하지만 첫 호출 시 어떻게? |
| 설계 재검토 후 클래스 수 변화 | 새 클래스 1 개 추가 vs 기존 결합 해소 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
─── 여기 아래는 선택 (시간 여유 시) ───
━━━━━━━━━━━━━━━━━━━━━━━━━━

### [선택] ▸ STAGE 5 — AOP 브릿지 (5 주차 보너스)

> ⏰ **언제 하나**: Ready PR (목 11:00) 이후 **여유 시에만**. STAGE 1~4 가 우선. 늦어도 **5 주차 시작 전 (다음 목)** 까지 안 해도 됨.

##### 5-1. `@Configuration(proxyBeanMethods)` — 프록시가 메서드 호출 가로채기

```java
@Configuration(proxyBeanMethods = true)   // 기본 — CGLIB 프록시
static class ConfigTrue {
    @Bean public SomeDependency dep() { return new SomeDependency(); }
    @Bean public String beanA() { dep(); return "A"; }   // 프록시가 가로채서 캐싱된 싱글톤 반환
    @Bean public String beanB() { dep(); return "B"; }   // 동일
}

@Configuration(proxyBeanMethods = false)  // Lite Mode — 프록시 없음
static class ConfigFalse {
    // 동일 코드. 단 dep() 호출 시 매번 new — 싱글톤 보장 X
}
```

**관찰 포인트**:
- true: `SomeDependency` 생성자 1 회만 호출
- false: 3 회 호출 (Bean 등록 1 + beanA 1 + beanB 1)
- → 자바 코드로는 분명히 메서드 호출했는데, 스프링이 중간에 프록시로 가로채서 다른 동작 (캐싱) 으로 바꿈

##### 5-2. `@Transactional` — 동일 프록시 원리

```java
@Service
public class TransferService {
    @Transactional
    public void transfer(long from, long to, BigDecimal amount) {
        // 이 메서드 호출 전후로 트랜잭션 begin / commit 이 자동 삽입됨
    }
}
```

- `ctx.getBean(TransferService.class)` 가 반환하는 객체는 **프록시** (CGLIB 또는 JDK Dynamic Proxy)
- 프록시가 어떻게 트랜잭션을 끼워넣는가 → 5 주차 AOP 본론
- `proxyBeanMethods` 와 `@Transactional` 이 **동일 메커니즘** (프록시로 메서드 호출 가로채기) 임을 STAGE 5-1 에서 직접 본 후 5 주차로 진입


━━━━━━━━━━━━━━━━━━━━━━━━━━
금지 키워드 — STAGE 1~2 동안만 (검색 금지)
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Spring AOP / `@Transactional` 의 내부 동작 (5 주차 보호)
- `@EnableAspectJAutoProxy` / `@Aspect` (5 주차)
- Spring Security 의 DI 패턴 (11 주차)
- Spring Cloud / `@FeignClient` (먼 미래)


━━━━━━━━━━━━━━━━━━━━━━━━━━
학습 기록 (자유)
━━━━━━━━━━━━━━━━━━━━━━━━━━

### 4 주차 참고 질문 (답하고 싶은 만큼만)
- IoC 가 "뒤집는" 것은 정확히 무엇인가 (제어의 흐름 / 객체 생성의 책임)
- 3 주차의 `DataSourceFactory` 싱글톤과 스프링 싱글톤 Bean 의 같은 점 / 다른 점
- 생성자 주입을 권장하는 이유 3 가지 (불변성 / 명시성 / 순환 참조 감지)
- `@Autowired` 가 같은 타입 Bean 여러 개를 만났을 때 동작 순서 (이름 매칭 → `@Primary` → 실패)
- Spring Boot 2.6+ 가 순환 참조를 막은 이유 — 어떤 사고를 막으려고
- 프로토타입 스코프의 `@PreDestroy` 가 호출 안 되는 이유
- `ApplicationContext` 와 `BeanFactory` 차이 — 언제 어느 쪽?
- 본인 도메인에서 다중 구현체가 자연스러운 부분 1 개

### 면접 단골 + 본인 답
- **"IoC 와 DI 차이는?"** (IoC = 패러다임, DI = 구체 구현)
- **"생성자 주입을 권장하는 이유 3 가지"** (final / 필수 명시 / 순환 참조 부팅 시점)
- **"Spring Boot 2.6+ 가 순환 참조를 막은 이유"** + 해결 방법
- **"`@Component` vs `@Bean` 차이"** (자동 vs 수동 / 본인 코드 vs 외부 라이브러리)
- **"싱글톤 Bean 의 멀티스레드 안전성"** (필드에 상태 없으면 OK)
- **"Bean 스코프 5 가지"** (Singleton / Prototype / Request / Session / Application — 뒤 3 개는 웹 환경 전용)
- **"`@PostConstruct` 호출 시점"** (생성자 → 의존성 주입 완료 → `@PostConstruct` → 사용 가능. 생성자에서 의존성 사용 못 하는 이유)
- **"DI 가 SOLID 의 DIP 를 어떻게 만족시키나"** (구체 클래스가 아니라 인터페이스에 의존 → 구현체 교체 자유 + OCP 자연 달성)

### 실무 확장 화두 (스터디 토론 / 면접 후속 질문)
- **`@Configuration` 의 `proxyBeanMethods` 옵션**: 기본 true 시 `@Bean` 메서드 호출이 캐시되는 메커니즘 (CGLIB 프록시). false 로 바꾸면 어떻게 되는가
- **`@ConditionalOnXxx`**: Spring Boot AutoConfiguration 의 핵심 — Bean 등록을 조건부로
- **`@Lazy` 의 부작용**: 첫 호출 시 지연 → 첫 사용자가 손해. 캐시 워밍업으로 보완
- **`BeanPostProcessor`**: AOP / `@Transactional` 의 동작 기반. Bean 생성 후처리 hook
- **Spring 의 3 단계 초기화** (Eager / Lazy / 트리거): 디버깅 시 Bean 이 어디서 막혔는지

### Bean 선택 매트릭스 (면접 답변 기준)

| 상황 | 선택 | 이유 |
|---|---|---|
| 본인 작성 클래스 (도메인 / 서비스) | `@Component` 계열 | 컴포넌트 스캔 자동 등록, 코드 간결 |
| 외부 라이브러리 객체 (DataSource / RedisClient) | `@Bean` | 소스 수정 불가, `@Configuration` 에 수동 등록 |
| 조건부 Bean (환경 / 프로필별) | `@Profile` + `@Bean` | 운영 / 개발 환경별 다른 구현체 |
| 의존성 그래프가 무겁고 부팅 느림 | `@Lazy` | 첫 호출 시점까지 생성 미룸 |
| 같은 타입 Bean 여러 개 | `@Qualifier` (명시) 또는 `@Primary` (기본값) | 명시 지정이 우선 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
막힐 때
━━━━━━━━━━━━━━━━━━━━━━━━━━
1. 직접 시도 (`CLAUDE.md` 룰)
2. AI 에 물어보기 — 3 단계 힌트만 받음 (코드 직답 X)
3. 디스코드 `#질문` 채널 — 코드 + 에러 메시지 + 본인 `@Configuration` 함께

특히 **`NoSuchBeanDefinitionException` 발생 시**: (a) 클래스에 `@Component` 계열 어노테이션 있는지 (b) `@ComponentScan` 의 basePackage 가 그 클래스 포함하는지 (c) 같은 타입 Bean 여러 개라면 `@Qualifier` 필요한지 — 순서대로 확인.

**`BeanCurrentlyInCreationException` 발생 시**: 순환 참조 발생. 위 STAGE 4-3 의 해결 3 가지 중 (a) 설계 재검토 먼저 시도.

**`NoUniqueBeanDefinitionException` 발생 시**: 같은 타입 Bean 이 여러 개. `@Qualifier` 로 명시 지정하거나 한쪽에 `@Primary`.
