# 4주차 측정 기록

## STAGE 1-1 - IoC 컨테이너 직접 관찰
날짜: 2026-05-26  
실행 클래스: `stage.s1.Stage1Lifecycle`
>  Spring 컨테이너가 Bean을 어떤 순서로 만들고, 의존성을 넣고, 초기화하고, 종료하는지 직접 눈으로 확인한다.

### 관찰 결과

- 컨테이너: `AnnotationConfigApplicationContext`를 직접 생성하고 `close()`까지 직접 호출
- Bean 수: 12개
- 주요 Bean 이름: `email`, `notificationService`, `lifecycleDependency`, `lifecycleSampleBean`, `prototypeDeliveryTrace`

### Bean 생성/초기화 순서

- `LifecycleSampleBean`: constructor → setter injection → `@PostConstruct` → use → `@PreDestroy`
- `EmailSender`: constructor → `@PostConstruct`
- `NotificationService`: constructor injection → `@PostConstruct`

### 의존성 주입 관찰

- `LifecycleSampleBean` 생성자에서는 `dependency = null`
- 세터 주입 이후 `@PostConstruct`에서는 의존성 사용 가능
- `NotificationService`는 `NotificationSender`를 직접 `new`하지 않고, 컨테이너가 만든 `email` Bean을 생성자로 주입받음

### 싱글톤 / 프로토타입

- `NotificationService` 2회 조회 결과: 같은 인스턴스
- `NotificationSender` 2회 조회 결과: 같은 인스턴스
- `PrototypeDeliveryTrace` 2회 조회 결과: 서로 다른 인스턴스 (`id=1`, `id=2`)
- 프로토타입 Bean은 `@PostConstruct`는 호출되지만, 컨테이너 종료 시 `@PreDestroy`는 호출되지 않음

### 스코프 메모

- 싱글톤 Bean은 Spring 컨테이너 안에서 하나만 생성되는 Bean이다.
- Spring의 기본 Bean scope는 싱글톤이다.
- 싱글톤 Bean은 컨테이너가 생성부터 소멸까지 관리하므로 `context.close()` 시 `@PreDestroy`가 호출된다.


- 프로토타입 Bean은 `getBean()`으로 요청할 때마다 새로 생성되는 Bean이다.
- 프로토타입 Bean은 컨테이너가 생성, 의존성 주입, `@PostConstruct`까지만 관리한다.
- 컨테이너는 프로토타입 Bean이 언제 더 이상 필요 없는지 추적하지 않으므로 `context.close()` 시 `@PreDestroy`를 호출하지 않는다.

### 컨테이너 종료 순서

- `LifecycleSampleBean` `@PreDestroy`
- `NotificationService` `@PreDestroy`
- `EmailSender` `@PreDestroy`

### 실행 명령

```bash
./gradlew.bat :topics:04-ioc-bean:members:sujin:run --project-prop mainClass=stage.s1.Stage1Lifecycle
```

### 해석

IoC 컨테이너가 Bean 생성, 의존성 연결, 초기화 콜백, 소멸 콜백을 관리한다.  
싱글톤 Bean은 컨테이너 종료 시 소멸 콜백까지 호출되지만, 프로토타입 Bean은 생성과 초기화까지만 컨테이너가 책임진다.

-------------------------------------------------------------------------------

## STAGE 1-2 - `@ComponentScan` vs `@Bean`

날짜: 2026-05-26  
실행 클래스: `stage.s1.Stage1ScanVsBean`
> 내가 작성한 클래스는 @ComponentScan으로 자동 등록하고, 외부 라이브러리 객체는 @Bean으로 수동 등록해야 하는 이유를 확인한다.

### 관찰 결과

- `@ComponentScan`: `domain` 패키지 아래의 `@Component`, `@Service`를 자동 등록
- `@Bean`: 설정 클래스에서 반환한 `NotificationSender` 하나만 수동 등록
- `@ComponentScan` Bean 수: 9개 (`email`, `notificationService` 포함)
- `@Bean` Bean 수: 8개 (`email`만 명시 등록)
- `@Bean`으로 등록한 `EmailSender`도 `@PostConstruct`, `@PreDestroy`가 호출됨

### 실행 명령

```bash
./gradlew.bat :topics:04-ioc-bean:members:sujin:run --project-prop mainClass=stage.s1.Stage1ScanVsBean
```

### 해석

본인이 작성한 도메인 클래스는 `@Component` 계열로 자동 등록할 수 있다.  
외부 라이브러리 객체(`DataSource`, `RedisClient`)는 소스에 어노테이션을 붙일 수 없으므로 `@Bean`으로 등록한다.

### 중복 등록 메모

같은 클래스를 `@Component`와 `@Bean`으로 동시에 등록하지 않는 것이 원칙이다.

- 같은 타입이지만 Bean 이름이 다르면 같은 타입 Bean이 2개 생긴다.
  - 예: `email`, `emailSender`
  - 이후 타입만으로 주입하려고 하면 어떤 Bean을 넣을지 몰라 `NoUniqueBeanDefinitionException`이 날 수 있다.
- Bean 이름까지 같으면 Bean definition 충돌이 날 수 있다.
  - 컴포넌트 스캔 중 이름 충돌이면 `ConflictingBeanDefinitionException`
  - Bean override 설정에 따라 `BeanDefinitionOverrideException`

정리하면 직접 작성한 도메인 클래스는 `@Component` 계열로, 외부 라이브러리 객체나 복잡한 생성 로직은 `@Bean`으로 역할을 나누는 편이 안전하다.

-------------------------------------------------------------------------------

## STAGE 1-3 - `getBean()` vs 생성자 주입

날짜: 2026-05-26  
실행 클래스: `stage.s1.Stage1GetBeanVsAutowired`
> `ApplicationContext.getBean()`으로 직접 조회하는 방식과 생성자 주입 방식의 차이를 비교하고, DI가 의존성을 더 명시적으로 드러내는 이유를 확인한다.

### 관찰 결과

- `getBean()` 직접 조회: 호출자가 Spring `ApplicationContext`를 알아야 함
- Service Locator 방식: `getBean()` 호출이 별도 클래스 안으로 숨지만, 클래스가 여전히 Spring 컨테이너에 의존함
- 생성자 주입 방식: 필요한 의존성이 생성자 파라미터에 드러남
- 생성자 주입 방식은 테스트에서 `new NotificationUseCase(mockService)`처럼 대체 객체를 넣기 쉽다.

### 실행 명령

```bash
./gradlew.bat :topics:04-ioc-bean:members:sujin:run --project-prop mainClass=stage.s1.Stage1GetBeanVsAutowired
```

### 해석

`getBean()`은 컨테이너에서 객체를 직접 찾는 방식이라 호출 코드가 Spring에 강하게 묶인다.  
생성자 주입은 필요한 의존성을 코드 구조에 드러내고, 객체 생성 책임을 컨테이너에 맡기면서도 도메인 코드를 Spring API에서 멀어지게 한다.

### ApplicationContext 메모

`ApplicationContext`는 Spring IoC 컨테이너의 대표 인터페이스다.
Bean 생성, 의존성 주입, 라이프사이클 관리, 환경 정보, 이벤트, Bean 조회를 담당한다.

`getBean()`은 컨테이너에서 Bean을 직접 조회하는 방법이다.
학습 코드, 애플리케이션 시작점, 동적 Bean 선택, prototype Bean 조회처럼 필요한 순간이 있다.

하지만 일반적인 Service / Repository 코드에서 의존성 주입 대신 `getBean()`을 사용하면
필요한 의존성이 코드 구조에 드러나지 않고 Spring 컨테이너에 강하게 결합된다.
따라서 기본은 생성자 주입을 사용하고, 동적 조회가 필요한 경우에만 제한적으로 사용한다.

-------------------------------------------------------------------------------

## STAGE 1-4 - `@SpringBootApplication` 자동 등록 Bean 수 확인

날짜: 2026-05-26  
실행 클래스: `stage.s1.Stage1BootCount`
> `@SpringBootApplication`이 컴포넌트 스캔과 자동 설정을 통해 순수 Spring보다 훨씬 많은 Bean을 등록한다는 점을 숫자로 확인한다.

### 관찰 결과

- 빈 `@SpringBootApplication` Bean 수: 97개
- notification 도메인 포함 `@SpringBootApplication` Bean 수: 99개
- 도메인 Bean 증가분: 2개 (`email`, `notificationService`)
- 자동 설정 / 인프라 Bean 예시:
  - `AutoConfigurationPackages`
  - `PropertyPlaceholderAutoConfiguration`
  - `SslAutoConfiguration`
  - `TaskExecutionAutoConfiguration`
  - `AopAutoConfiguration`
  - `JacksonAutoConfiguration`
- 실험 안정화를 위해 웹 서버는 띄우지 않고, `DataSource`, JPA, Redis 자동 설정은 제외했다.
- DevTools restart / LiveReload도 꺼서 Bean 수 관찰 로그가 중복 실행되지 않도록 했다.

### 실행 명령

```bash
./gradlew.bat :topics:04-ioc-bean:members:sujin:run --project-prop mainClass=stage.s1.Stage1BootCount
```

### 해석

`@SpringBootApplication`은 단순히 내 클래스를 스캔하는 것에서 끝나지 않는다.  
`@EnableAutoConfiguration`이 classpath와 설정을 보고 Jackson, TaskExecutor, AOP 등 여러 인프라 Bean을 자동 등록한다.  
그래서 내가 만든 도메인 Bean은 2개뿐이어도 전체 Bean 수는 100개 가까이 된다.

### classpath 메모

classpath는 애플리케이션 실행 시 사용할 수 있는 클래스와 라이브러리 목록이다.
Spring Boot는 classpath에 어떤 라이브러리가 있는지 확인하고 자동 설정 여부를 판단한다.

예를 들어 `spring-boot-starter-web`이 있으면 Jackson, WebMvc 관련 자동 설정 후보가 활성화된다.
`application.yml` 같은 설정값은 이 자동 설정이 실제 Bean을 만들 때 참고하는 입력값이다.

-------------------------------------------------------------------------------

## STAGE 1-5 - STAGE 1 결과 정리

날짜: 2026-05-26  
> STAGE 1에서 직접 관찰한 IoC 컨테이너, Bean 라이프사이클, Bean 등록 방식, Boot 자동 설정 결과를 발표용으로 요약한다.

### 최종 요약

| 항목 | 관찰 결과 |
|---|---|
| 라이프사이클 순서 | constructor → dependency injection → `@PostConstruct` → use → `@PreDestroy` |
| 생성자 시점 | 세터 주입 의존성은 아직 `null` |
| `@PostConstruct` 시점 | 의존성 주입이 끝난 뒤라 의존 객체 사용 가능 |
| 싱글톤 Bean | 컨테이너당 하나의 인스턴스, `close()` 시 `@PreDestroy` 호출 |
| 프로토타입 Bean | `getBean()`마다 새 인스턴스, `@PostConstruct` 호출, `@PreDestroy` 미호출 |
| `@ComponentScan` | 내가 작성한 `@Component`, `@Service` 계열 클래스를 패키지 기준으로 자동 등록 |
| `@Bean` | 설정 클래스에서 반환한 객체를 수동 등록. 외부 라이브러리 객체 등록에 필요 |
| `getBean()` | 호출 코드가 Spring `ApplicationContext`에 직접 의존 |
| 생성자 주입 | 의존성이 생성자에 명시되고 테스트에서 대체 객체 주입이 쉬움 |
| 빈 Boot Bean 수 | 97개 |
| notification 포함 Boot Bean 수 | 99개 |

### 발표용 한 줄 정리

- IoC는 객체 생성과 의존성 연결의 책임을 내 코드에서 Spring 컨테이너로 넘기는 것이다.
- DI는 IoC를 구현하는 방식 중 하나이며, 생성자 주입은 필수 의존성을 명확히 드러낸다.
- Bean은 컨테이너가 생성, 초기화, 소멸을 관리하는 객체다.
- `@ComponentScan`은 내 코드의 자동 등록에 적합하고, `@Bean`은 외부 객체를 수동 등록할 때 필요하다.
- `@SpringBootApplication`은 컴포넌트 스캔뿐 아니라 자동 설정으로 많은 인프라 Bean을 추가한다.

### STAGE 1 체크리스트

- [x] `AnnotationConfigApplicationContext` 직접 생성
- [x] Bean 생성 / 주입 / 초기화 / 소멸 순서 확인
- [x] 싱글톤과 프로토타입 스코프 차이 확인
- [x] 프로토타입 Bean의 `@PreDestroy` 미호출 확인
- [x] `@ComponentScan`과 `@Bean` 차이 확인
- [x] `getBean()` 직접 조회와 생성자 주입 비교
- [x] `@SpringBootApplication` 자동 등록 Bean 수 확인
- [x] STAGE 1 관찰 결과 정리

### 다음 단계 연결

STAGE 2-1에서는 3주차의 `DataSourceFactory` 직접 싱글톤 생성 방식을 `@Configuration + @Bean(destroyMethod = "close")`로 옮긴다.  
STAGE 1-2에서 확인한 `@Bean`의 존재 이유가 여기서 바로 이어진다.

-------------------------------------------------------------------------------

## STAGE 2-1 - 3주차 Factory를 `@Bean`으로 마이그레이션

날짜: 2026-05-26  
실행 클래스: `stage.s2.Stage2Migration`
> 3주차에서 직접 만들고 닫던 `DataSourceFactory`, `RedisClientFactory`를 Spring Bean으로 옮겨 생성과 종료 책임을 컨테이너에 맡긴다.

### Before - 3주차 방식

- `DataSourceFactory.create(poolSize)`로 HikariCP pool을 직접 생성
- `DataSourceFactory.close(dataSource)`를 main의 `finally`에서 직접 호출
- `RedisClientFactory`는 static singleton을 만들고 `shutdown()`을 직접 호출
- 사용하는 코드가 factory class를 직접 알아야 함

### After - 4주차 방식

- `DataSourceConfig`에서 `@Bean(destroyMethod = "close")`로 `DataSource` 등록
- `RedisConfig`에서 `@Bean(destroyMethod = "shutdown")`으로 `RedisClient` 등록
- `NotificationLogRepository`는 `DataSource`를 생성자 주입으로 받음
- `context.close()`가 Bean의 destroy method를 자동 호출

### 관찰 결과

- Before DataSource type: `HikariDataSource`
- Before RedisClient type: `RedisClient`
- Before DB connection: success, catalog=`csstudy`
- Before cleanup: `DataSourceFactory.close(dataSource)`, `RedisClientFactory.shutdown()`에 해당하는 직접 호출 필요
- After DataSource type: `HikariDataSource`
- After RedisClient type: `RedisClient`
- After repository injected DataSource type: `HikariDataSource`
- After DB connection: success, catalog=`csstudy`
- After cleanup: `context.close()` 시 `destroyMethod = "close"`로 HikariCP shutdown 자동 호출

### 실행 명령

```bash
./gradlew.bat :topics:04-ioc-bean:members:sujin:run --project-prop mainClass=stage.s2.Stage2Migration
```

### 해석

3주차에서는 객체 생성과 종료 순서를 애플리케이션 코드가 직접 기억해야 했다.  
4주차 방식에서는 `DataSource`, `RedisClient`가 Spring Bean이 되므로, 다른 Bean에 주입할 수 있고 컨테이너 종료 시 정리 메서드도 자동 호출된다.

-------------------------------------------------------------------------------

## STAGE 2-3 - 주입 방식 3가지 비교 (생성자 / 필드 / 세터)

날짜: 2026-05-29  
실행 클래스: `stage.s2.Stage2InjectionTypes`
> 같은 `NotificationSender` 의존성을 생성자 / 필드 / 세터 3가지 방식으로 받는 클래스를 동일 컨테이너에 띄우고, `final` 가능 여부 / 컨테이너 없이 `new` 했을 때 동작 / 테스트 mock 주입 코드 길이를 비교한다.

### 컨테이너 구성

- `AnnotationConfigApplicationContext` + `InjectionConfig` 직접 등록 (Spring Boot 자동 설정 사용 안 함)
- `EmailSender` 는 `@ComponentScan` 대신 `@Bean(name = "email")` 로 1회만 등록
- 같은 `email` Bean 이 3개 소비자 클래스에 주입되는지 검증하려는 의도

### 관찰 결과 - 라이프사이클 순서

- `EmailSender` constructor → `EmailSender` `@PostConstruct`
- `CtorInjected` constructor 호출 (sender 인자 전달)
- `SetterInjected.setSender(...)` 호출
- (`FieldInjected` 는 리플렉션 주입이라 별도 출력 없음)
- `context.close()` 시 `EmailSender` `@PreDestroy`

### 관찰 결과 - 싱글톤 검증 (같은 Bean 이 3 곳에 주입됐는가)

| 비교 | 결과 |
|---|---|
| `ctor.sender   == email Bean` | true |
| `field.sender  == email Bean` | true |
| `setter.sender == email Bean` | true |

세 클래스 모두 컨테이너 안의 같은 `email` Bean 인스턴스를 가리킨다. Spring 의 기본 스코프 (싱글톤) 가 작동했음.

### 관찰 결과 - 컨테이너 없이 `new` 했을 때

| 방식 | `new` 직후 동작 | 원인 |
|---|---|---|
| 생성자 | 정상 - `[ctorNew]` 메시지가 정상 전송됨 | 생성자 파라미터가 의존성을 강제 |
| 필드 | `NullPointerException` 발생 | `@Autowired` 가 리플렉션 주입이라 컨테이너 없이는 `sender == null` |
| 세터 | `NullPointerException` 발생 | `setSender()` 호출 전에는 `sender == null` |

### 방식별 비교 표

| 항목 | 생성자 주입 | 필드 주입 | 세터 주입 |
|---|---|---|---|
| `final` 가능 | O | X (리플렉션 주입이 final 필드 못 채움) | X (setter 가 재할당) |
| 테스트 mock 주입 라인 | 1줄 - `new Target(mock)` | 2~3줄 - `new Target() + ReflectionTestUtils.setField(...)` | 2줄 - `new Target() + target.setSender(mock)` |
| 컨테이너 없이 사용 가능 | O | X (NPE) | X (setter 호출 전 NPE) |
| 순환 참조 감지 시점 | 부팅 시점 즉시 (`BeanCurrentlyInCreationException`) | Spring Boot 2.6+ 부팅 실패. 옛 버전은 런타임 NPE 가능 | 동일 |
| 선택적 의존성 표현 | 어색함 (오버로드 필요) | 가능 (`required = false`) | 가장 자연스러움 |

### 실행 명령

```bash
./gradlew.bat :topics:04-ioc-bean:members:sujin:run --project-prop mainClass=stage.s2.Stage2InjectionTypes
```

### 해석

생성자 주입은 (a) 불변성 - `final` 보장, (b) 명시성 - 의존성이 시그니처에 드러남, (c) 부팅 시점에 순환 참조 감지, 3가지를 동시에 제공하므로 기본은 생성자 주입을 사용한다.

필드 주입은 코드는 가장 짧지만 `final` 불가 + 리플렉션 의존 + 컨테이너 없이 사용 불가 + 테스트 시 추가 코드 (`ReflectionTestUtils`) 가 필요하므로 비권장이다.

세터 주입은 선택적 의존성 (예: `required = false`) 표현에는 자연스럽지만, 필수 의존성에 쓰면 객체 생성 직후 잠시 `null` 상태가 존재한다.

### Bean 이름 메모

`@Component("email")` 처럼 이름을 명시하면 Bean 이름이 그대로 `"email"` 이 된다.  
이름을 명시하지 않은 `@Component` 만 붙으면 Bean 이름은 클래스명 첫 글자만 소문자로 바꾼 `"emailSender"` 가 된다.  
STAGE 2-4 의 `Map<String, NotificationSender>` 자동 주입에서 이 차이가 곧 키 차이로 드러난다.

-------------------------------------------------------------------------------

## STAGE 2-4 - 다중 구현체 + `@Qualifier` / `Map<String, NotificationSender>`

날짜: 2026-05-29  
실행 클래스: `stage.s2.Stage2Qualifier`, `stage.s2.Stage2MapInjection`
> 같은 타입 `NotificationSender` Bean 4개 (Email / Sms / Push / Slack) 가 등록된 상태에서, `@Qualifier` 명시 주입과 `Map<String, NotificationSender>` 자동 주입 두 방식을 비교한다. 의도적으로 Bean 이름 정책을 다양화해서 디폴트 이름 vs 명시 이름 차이가 Map 의 키 차이로 드러나는지 관찰한다.

### Bean 이름 정책 (의도적으로 4가지 섞음)

| 클래스 | 어노테이션 | Bean 이름 | 이유 |
|---|---|---|---|
| `EmailSender` | `@Component("email")` | `email` | 명시 |
| `SmsSender` | `@Component` | `smsSender` | 디폴트 (클래스명 camelCase) |
| `PushSender` | `@Component("push")` | `push` | 명시 |
| `SlackSender` | `@Component` | `slackSender` | 디폴트 |

### Case A. `@Qualifier` 없이 같은 타입 1개 주입 시도 → 부팅 실패

- 같은 타입 Bean 이 4개라 후보를 좁히지 못함
- `UnsatisfiedDependencyException` 의 root cause 가 `NoUniqueBeanDefinitionException`
- 메시지: "expected single matching bean but found 4: email, smsSender, push, slackSender"
- 해결책: `@Qualifier` 명시 주입 또는 `@Primary` (다음 STAGE 2-5 에서 비교)

### Case B. `@Qualifier` 명시 주입

| Consumer | `@Qualifier` | 주입된 sender |
|---|---|---|
| `emailConsumer` | `"email"` | `EmailSender` |
| `smsConsumer` | `"smsSender"` | `SmsSender` |
| `pushConsumer` | `"push"` | `PushSender` |
| `slackConsumer` | `"slackSender"` | `SlackSender` |

`@Qualifier` 의 값은 **Bean 이름** 이다. 명시 이름 (`"email"`, `"push"`) 이든 디폴트 이름 (`"smsSender"`, `"slackSender"`) 이든 정확히 일치해야 한다.

### `Map<String, NotificationSender>` 자동 주입

Spring 은 같은 타입 Bean 이 여러 개일 때 `Map<BeanName, Bean>` 형태로 한꺼번에 받을 수 있다.

```java
public Dispatcher(Map<String, NotificationSender> senders) { ... }
```

주입 결과:

| 키 | 값 |
|---|---|
| `email` | `EmailSender` |
| `smsSender` | `SmsSender` |
| `push` | `PushSender` |
| `slackSender` | `SlackSender` |

`dispatcher.dispatch("email", ...)` / `dispatch("push", ...)` 는 정상 동작.  
`dispatcher.dispatch("sms", ...)` 는 `Map.get` 이 `null` 을 반환해서 "등록된 sender 없음" 으로 안내 — 디폴트 이름은 `"smsSender"` 이지 `"sms"` 가 아니라는 점을 직접 확인.

### 실행 명령

```bash
./gradlew.bat :topics:04-ioc-bean:members:sujin:run --project-prop mainClass=stage.s2.Stage2Qualifier
./gradlew.bat :topics:04-ioc-bean:members:sujin:run --project-prop mainClass=stage.s2.Stage2MapInjection
```

### 해석

같은 타입 Bean 이 여러 개일 때 Spring 은 후보를 자동으로 좁히지 못한다.  
해결책은 (a) `@Qualifier` 로 Bean 이름 명시, (b) `@Primary` 로 기본값 표시, (c) `Map<String, T>` 로 전부 받아서 직접 분기 — Strategy 패턴 자동화.

`Map<String, NotificationSender>` 자동 주입은 Strategy 패턴을 가장 자연스럽게 표현한다. 새 sender 가 추가되면 클래스 1개만 추가하면 되고 (OCP), Dispatcher 코드는 변경 없다.

`@Component` 의 이름을 일관성 있게 명시 또는 디폴트로 통일하는 편이 Map 키 매핑 실수를 줄인다. 본 학습에서는 차이를 보려고 일부러 섞었다.

-------------------------------------------------------------------------------

## STAGE 2-5 - `@Primary` vs `@Qualifier` 우선순위

날짜: 2026-05-29  
실행 클래스: `stage.s2.Stage2PrimaryConflict`
> 같은 타입 Bean 2개 (email / smsSender) 상태에서 `@Primary` 와 `@Qualifier` 가 동시에 있을 때 어느 쪽이 이기는지 3가지 케이스로 확인한다.

### 케이스 설계

3가지 케이스를 각각 별도 `@Configuration` + 별도 `ApplicationContext` 로 격리해서 결과를 비교한다.  
`EmailSender` 클래스 자체에는 `@Primary` 를 영구 추가하지 않고, 케이스별 `@Bean` 메서드에만 표시 — 다른 시연 (Stage2Layering 등) 에 영향 안 주려는 의도.

### 관찰 결과

| Case | `email` Bean | Consumer 시그니처 | 주입된 sender | 우선 규칙 |
|---|---|---|---|---|
| 1. `@Primary` 만 | `@Primary` | `(NotificationSender sender)` — 명시 X | `EmailSender` | `@Primary` 가 "기본값" 으로 작동 |
| 2. `@Qualifier` 만 | (없음) | `@Qualifier("smsSender")` | `SmsSender` | 명시 지정이 작동 |
| 3. 둘 다 | `@Primary` | `@Qualifier("smsSender")` | `SmsSender` ★ | **`@Qualifier` 가 `@Primary` 를 이김** |

### 실행 명령

```bash
./gradlew.bat :topics:04-ioc-bean:members:sujin:run --project-prop mainClass=stage.s2.Stage2PrimaryConflict
```

### 해석

`@Primary` 는 "후보가 여러 개 남았을 때의 tiebreak" 역할이고, `@Qualifier` 는 "후보를 1개로 강제 지정" 한다.  
`@Qualifier` 가 있으면 후보 단계에서 이미 1개로 좁혀지므로 `@Primary` 판단 단계 자체에 안 들어간다.  
그래서 Case 3 처럼 둘 다 있어도 `@Qualifier` 가 이긴다.

### 면접 답변 한 줄

- "같은 타입 Bean 다수 + `@Primary` + `@Qualifier` 동시 사용 시 누가 이기나?"
- **`@Qualifier` 가 이긴다. `@Primary` 는 후보가 여러 개일 때의 기본값이고, `@Qualifier` 는 후보를 명시 지정해서 이미 1개로 좁힌다.**

### 사용 가이드 (실무 기준)

| 상황 | 선택 |
|---|---|
| 같은 타입 구현체 중 "거의 항상 이걸 쓴다" 가 있음 | `@Primary` |
| 호출 측 / 환경에 따라 다른 구현체를 명시적으로 골라야 함 | `@Qualifier` |
| Strategy 패턴 (런타임에 키로 분기) | `Map<String, T>` 자동 주입 |

`@Primary` 는 "묵시적 기본값" 이라 향후 다른 구현체 추가 시 의도와 다른 곳에 주입될 수 있다.  
규모가 커질수록 `@Qualifier` 명시가 안전.

-------------------------------------------------------------------------------
