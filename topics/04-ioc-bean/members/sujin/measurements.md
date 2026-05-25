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
