# 4주차 Spring IoC / DI / Bean — 용어 정리

> 3 주차의 락 용어 정리와 같은 형식. STAGE 진행 전 또는 학습 중 막힐 때 참조.
>
> 시나리오 단어표 (14 개) 는 핵심만, 이 파일은 카테고리별 전체.

---

## 🌱 IoC / DI 핵심

| 용어 | 풀어쓰면 |
|---|---|
| **IoC** (Inversion of Control) | 제어의 역전. 객체 생성 / 의존성 연결의 주체를 본인 코드 → 프레임워크로 뒤집음 |
| **DI** (Dependency Injection) | IoC 의 구체 구현. 컨테이너가 객체에 필요한 의존성을 주입 |
| **Bean** | 스프링 컨테이너가 관리하는 객체 (라이프사이클을 컨테이너가 책임) |
| **ApplicationContext** | 스프링의 IoC 컨테이너 본체 |
| **BeanFactory** | ApplicationContext 의 부모 인터페이스. 가장 기본적인 컨테이너 |
| **BeanDefinition** | Bean 의 메타데이터 (클래스 / 스코프 / 의존성). 실제 객체가 아닌 설계도 |
| **DIP** (Dependency Inversion Principle) | SOLID 의 D. 구체 클래스 X, 추상 (인터페이스) 에 의존 — DI 가 자동화 |

## 🏷 Bean 등록 어노테이션

| 용어 | 풀어쓰면 |
|---|---|
| `@Component` | 일반 컴포넌트. 컴포넌트 스캔으로 자동 Bean 등록 |
| `@Service` | 비즈니스 로직 계층. `@Component` 의 의미 분류 |
| `@Repository` | 데이터 접근 계층. `@Component` + JDBC 예외 변환 |
| `@Controller` | 웹 요청 처리 계층 |
| `@Configuration` | 코드로 Bean 등록하는 설정 클래스 |
| `@Bean` | `@Configuration` 안에서 메서드로 Bean 등록 (외부 라이브러리 필수) |
| `@ComponentScan` | basePackages 부터 모든 `@Component` 자동 등록 |
| **ConflictingBeanDefinitionException** | 같은 Bean 이름 중복 등록 시 |

## 💉 의존성 주입

| 용어 | 풀어쓰면 |
|---|---|
| `@Autowired` | 타입 기반 의존성 주입 |
| `@Qualifier` | 같은 타입 Bean 여러 개일 때 이름으로 명시 지정 |
| `@Primary` | 같은 타입 여러 개일 때 "기본값" 지정. `@Qualifier` 가 우선 |
| `@Resource` | 자바 표준 (JSR-250). 이름 → 타입 순으로 조회 |
| `@Inject` | JSR-330. `@Autowired` 와 거의 동일 |
| **생성자 주입** | 권장. final 가능 / 필수 의존성 명시 / 순환 참조 부팅 시점 감지 |
| **필드 주입** | `@Autowired` 필드. 리플렉션 사용. 테스트 어려움 |
| **세터 주입** | `@Autowired` 세터 메서드. 선택적 의존성에만 가끔 |
| **리플렉션** (Reflection) | 자바 런타임에 클래스 / 필드 / 메서드 접근. 필드 / 세터 주입의 메커니즘 |
| **NoUniqueBeanDefinitionException** | 같은 타입 Bean 여러 개 + `@Qualifier` / `@Primary` 없음 |
| **NoSuchBeanDefinitionException** | 주입 대상 Bean 이 컨테이너에 없음 |

## 🔄 라이프사이클

| 용어 | 풀어쓰면 |
|---|---|
| **생성자** | Bean 생성 첫 단계. 의존성 아직 주입 X (필드 / 세터 주입 시) |
| `@PostConstruct` | 의존성 주입 완료 후 호출. 초기화 작업 (jakarta.annotation) |
| `@PreDestroy` | 컨테이너 종료 시 호출. 자원 해제 |
| `destroyMethod` | `@Bean(destroyMethod = "close")` — 종료 시 호출할 메서드 명시 |
| `initMethod` | `@Bean(initMethod = "init")` — 초기화 시 호출 |
| **순서** | 생성자 → 의존성 주입 → `@PostConstruct` → 사용 → `@PreDestroy` |
| **BeanPostProcessor** | Bean 생성 후 가공 hook. AOP / `@Transactional` 동작 기반 |
| `InitializingBean.afterPropertiesSet()` | `@PostConstruct` 의 옛 방식 (인터페이스 구현) |
| `DisposableBean.destroy()` | `@PreDestroy` 의 옛 방식 |

## 📦 Bean 스코프 5 가지

| 용어 | 풀어쓰면 |
|---|---|
| **Singleton** (기본) | 컨테이너당 1 개만 생성 → 모든 곳에서 같은 인스턴스 |
| **Prototype** | 요청할 때마다 새 인스턴스. 소멸은 컨테이너 책임 X |
| **Request** | HTTP 요청 단위. 웹 환경 전용 |
| **Session** | HTTP 세션 단위. 웹 환경 전용 |
| **Application** | 서블릿 컨텍스트 1 개. 웹 환경 전용 |
| `@Scope("prototype")` | 스코프 명시 어노테이션 |

## 🎯 다형성 / 디자인 패턴 / SOLID

| 용어 | 풀어쓰면 |
|---|---|
| **Strategy 패턴** | 인터페이스 + 다중 구현체로 알고리즘 / 동작 교체 |
| **Service Locator 패턴** | `ctx.getBean()` 으로 직접 조회. **안티패턴** — 컨테이너 강결합 |
| **팩토리 패턴** | 객체 생성 책임을 별도 클래스로 — `@Configuration` + `@Bean` 이 이 역할 |
| **OCP** (Open/Closed Principle) | 확장 열림 / 수정 닫힘. 새 구현체 추가 = 클래스 1 개 추가 (코드 수정 X) |
| **DIP** (Dependency Inversion) | 구체 X, 추상에 의존. DI 의 본질 |
| **결합도** (Coupling) | 클래스 간 의존 강도. DI 가 낮춤 |
| **응집도** (Cohesion) | 한 클래스의 책임 집중도 |
| **의존성 그래프** | Bean 간 의존 관계의 방향성 그래프 (순환 = 데드락의 IoC 버전) |
| **Map<String, T> 자동 주입** | 같은 타입 모든 Bean 을 Map 으로 받음. 키 = Bean 이름 |
| **Bean 이름** | `@Component("name")` 의 value 또는 클래스명 camelCase (`EmailSender` → `emailSender`) |

## 🔁 순환 참조

| 용어 | 풀어쓰면 |
|---|---|
| **Circular Reference** | A → B → A 의존. Spring Boot 2.6+ 부터 부팅 시 차단 |
| **BeanCurrentlyInCreationException** | 생성자 순환 참조 시 부팅 실패 예외 |
| **UnsatisfiedDependencyException** | 의존성 해결 실패. 순환 참조의 일반 형태 |
| **닭·달걀 문제** | 생성자 순환 시 — A 만들려면 B 필요, B 만들려면 A 필요 |
| **빈 껍데기 메커니즘** (Early Bean Reference) | 필드 / 세터 주입은 빈 인스턴스 먼저 만든 후 주입 → 옛날 통과 |
| **3 단계 캐시** | singletonObjects / earlySingletonObjects / singletonFactories — 빈 껍데기 메커니즘 내부 구조 |
| `setAllowCircularReferences(false)` | 빈 껍데기 메커니즘 차단. Spring Boot 2.6+ 기본값 |
| `spring.main.allow-circular-references` | Spring Boot 설정. true 강제 시 옛 동작 |
| `@Lazy` | 한쪽을 프록시로 만들어서 순환 회피. 동작은 하지만 설계 냄새 |
| **Mediator 패턴** | A 와 B 의 공통 책임을 제 3 자로 분리 — 순환 근본 해결 |

## 🚀 Spring Boot

| 용어 | 풀어쓰면 |
|---|---|
| `@SpringBootApplication` | `@Configuration` + `@ComponentScan` + `@EnableAutoConfiguration` 합성 |
| `@EnableAutoConfiguration` | 의존성에 따라 100~200 개 Bean 자동 등록 |
| `@ConditionalOnXxx` | 조건부 Bean 등록 (`@ConditionalOnClass`, `@ConditionalOnMissingBean` 등) |
| **AutoConfiguration** | `spring-boot-starter-*` 가 추가하는 자동 설정들 |
| **spring-boot-starter** | 의존성 묶음 패키지 (core / web / data-jpa / security 등) |
| `ConfigurableApplicationContext` | `SpringApplication.run()` 의 반환 타입 |
| `getBeanDefinitionCount()` | 등록된 Bean 수 |
| `getBeanDefinitionNames()` | 등록된 Bean 이름 배열 |
| `application.properties` / `application.yml` | Spring Boot 가 자동 로드하는 설정 파일 |
| `@Profile` | 환경별 Bean 분기 (dev / prod) |

## 🔮 프록시 / AOP 브릿지 (5 주차 예고)

| 용어 | 풀어쓰면 |
|---|---|
| `proxyBeanMethods` | `@Configuration(proxyBeanMethods = true/false)`. true = CGLIB 프록시, false = Lite Mode |
| **CGLIB** (Code Generation Library) | 런타임 클래스 바이트코드 조작. 인터페이스 없는 클래스도 프록시 가능 |
| **JDK Dynamic Proxy** | 자바 표준 프록시. 인터페이스 기반 |
| **Lite Mode** | `@Configuration(proxyBeanMethods = false)` — 프록시 없이 순수 자바 동작 |
| **프록시 패턴** | 실제 객체 앞에 대리 객체 — 호출 가로채기 가능 |
| **메서드 호출 가로채기** (Interception) | 프록시가 메서드 호출 전후로 로직 추가 |
| `@Transactional` | 5 주차 본론. 프록시가 begin / commit 자동 삽입 |
| `@Aspect` | 5 주차. AOP 적용 클래스 표시 |
| `@EnableAspectJAutoProxy` | 5 주차. AOP 활성화 |

## ⚙️ 측정 / JVM

| 용어 | 풀어쓰면 |
|---|---|
| `System.nanoTime()` | 나노초 정밀도 시간. 부팅 시간 측정 |
| `StopWatch` | Spring 제공 측정 도구 |
| **JIT** (Just-In-Time) 컴파일 | 핫스팟 코드를 네이티브로 컴파일 — 반복 호출 시 빨라짐 |
| **JIT 웜업** | 첫 실행은 인터프리터, 이후 JIT 컴파일 → 측정 시 첫 회 느림 |
| `AtomicInteger` | 락 없이 안전한 카운터. 싱글톤 vs 프로토타입 카운트 측정 |
| **HotSpot JVM** | 표준 자바 가상 머신. JIT 동작의 주체 |
| **Lazy Initialization** | 첫 호출 시점까지 초기화 미룸 (`@Lazy`) |
| **Eager Initialization** | 부팅 시 즉시 초기화 (기본 동작) |

## 🛠 외부 객체 등록 (3 주차 → 4 주차 브릿지)

| 용어 | 풀어쓰면 |
|---|---|
| `@Bean(destroyMethod = "close")` | 종료 시 close() 자동 호출. HikariDataSource / RedisClient 등 |
| **HikariDataSource Bean 등록** | 3 주차 `DataSourceFactory` 싱글톤 → `@Configuration + @Bean` |
| **RedisClient Bean 등록** | 3 주차 `RedisClientFactory` → `@Bean(destroyMethod = "shutdown")` |
| **정적 싱글톤 vs Spring Bean** | static 초기화 vs 컨테이너 관리. 후자가 mock / 테스트 가능 |
| **생명주기 위임** (Lifecycle Delegation) | 본인이 close() 호출 → 컨테이너가 자동 호출 |

---

## ★ STAGE 1 진입 관문 (입으로 답)

1. **IoC 가 "뒤집는" 것** — 객체 생성 / 의존성 연결의 책임을 본인 코드 → 컨테이너로
2. **생성자 주입 권장 이유 3 가지** — final 가능 / 필수 의존성 명시 / 순환 참조 부팅 시점 감지
3. **Spring Boot 2.6+ 가 순환 참조 막은 이유** — 빈 껍데기 메커니즘이 race 에서 NPE / 부분 동작 유발
