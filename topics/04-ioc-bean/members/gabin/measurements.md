# 측정 기록

자동 누적. 옆에 해석 메모는 직접 추가하세요.

- [05-29 01:00] s1-1 · Bean lifecycle: 싱글톤 @PreDestroy 호출 / 프로토타입 @PreDestroy 미호출 / p1==p2 false / 2428ms
- [05-29 01:04] s1-1 · Bean lifecycle: 싱글톤 @PreDestroy 호출 / 프로토타입 @PreDestroy 미호출 / p1==p2 true / 1219ms -> scope 설정 안 해줘서 싱글톤으로 기본 설정될 떄

> [05-29 01:17] s1-1 · Bean lifecycle: 
  싱글톤 Bean 라이프사이클
  [1] SampleBean 생성자 호출
  [2] SampleBean @PostConstruct
  [3] getBean() 후 사용 - SampleBean
  [4] SampleBean @PreDestroy 
<br>
  프로토타입 Bean 라이프사이클
  [1] PrototypeBean 생성자 호출
  [2] PrototypeBean @PostConstruct
  [3] getBean() 두 번 호출
  p1 == p2 ? false
  @PreDestroy 호출 여부: 호출 안 됨

  관찰: singleton은 컨테이너 종료 시 소멸 콜백까지 관리하고, prototype은 생성까지만 관리한다.
  실행 시간: 1470ms

---
- [05-29 01:49] s1-2 · @ComponentScan vs @Bean:
> @ComponentScan 방식
> - Bean 이름: sampleBean
> - 등록 정보: com.example.study.sample.SampleBean
> - Scope: singleton
> - ctx.getBean(SampleBean.class)로 SampleBean 조회 성공

> @Bean 직접 등록 방식
> - Bean 이름: sampleBean
> - 등록 정보: stage1ScanVsBean.BeanConfig#sampleBean()
> - Scope: singleton(default)
> - ctx.getBean("sampleBean", SampleBean.class)로 SampleBean 조회 성공

  관찰
  - @ComponentScan은 지정한 패키지 아래의 @Component 클래스를 **자동 등록**한다.
  - @Bean은 @Configuration 클래스의 메서드 반환 객체를 직접 Bean으로 등록한다.
  - 외부 라이브러리 객체처럼 @Component를 붙일 수 없는 객체는 @Bean 방식으로 등록한다.
  - 자동스캔을 쓰면 편리해지지만 객체 생성 과정을 명시적으로 통제하는 힘을 일부 잃게 된다. 


- [05-29 02:29] s1-3 · getBean() vs constructor DI: 
  getBean() 직접 조회
  - 호출 코드가 ApplicationContext를 직접 알고 있다.
  - 필요한 Bean을 사용할 때마다 ctx.getBean(SampleBean.class)로 꺼낸다.
  - 조회 결과: SampleBean

  생성자 주입 DI
  - LifecycleReportService는 ApplicationContext를 모른다.
  - 생성자 파라미터로 SampleBean 필요성을 선언한다.
  - Spring이 SampleBean을 찾아 생성자 인자로 넣어준다.
  - 실행 결과: DI로 받은 Bean = SampleBean

  관찰
  - getBean()은 Service Locator 방식에 가깝고 코드가 Spring 컨테이너에 직접 의존한다.
  - 생성자 주입은 의존성이 생성자에 명시되고, 서비스는 컨테이너가 아니라 필요한 객체에만 의존한다.


- [05-29 02:54] s1-4 · @SpringBootApplication Bean count: 
  총 Bean 수: 209
  부팅 시간: 4341ms

  관찰
  - @SpringBootApplication은 @SpringBootConfiguration, @EnableAutoConfiguration, @ComponentScan을 포함한다.
  - 내가 직접 등록하지 않은 Spring Boot 기반 Bean도 자동으로 등록된다.
  - 이번 S1-4는 DB/Redis 실습이 아니므로 DataSource/JPA/Redis 자동설정은 제외했다.
  - DB 연결이 필요한 S2 이후에는 spring.datasource 설정과 Docker DB 실행이 필요하다.
---


- [05-29 03:21] s2-1 · DataSourceFactory -> @Bean: 
  Before
  - LegacyDataSourceFactory.create()로 DataSource를 직접 생성했다.
  - 사용이 끝나면 LegacyDataSourceFactory.close(ds)를 직접 호출해야 했다.

  After
  - DataSourceConfig.dataSource()를 @Bean으로 등록했다.
  - DataSource 조회는 ctx.getBean(DataSource.class)로 했다.
  - 컨테이너 종료 시 destroyMethod="close"가 HikariDataSource.close()를 호출한다.

  관찰
  - 객체 생성 책임이 애플리케이션 코드에서 Spring 컨테이너로 이동했다.
  - 자원 정리 책임도 직접 close 호출에서 Bean lifecycle의 destroy 단계로 이동했다.
  - DB 연결 테스트가 아니라 생성/소멸 책임 이전 관찰이므로 getConnection()은 호출하지 않았다.

---

- [05-29 06:58] s2-2 · discount domain layering: 
  도메인 계층
  - DiscountPolicy: 할인 계산 인터페이스
  - PercentDiscount / FixedDiscount / GradeDiscount: 할인 정책 구현체
  - OrderService: 주문 금액에 할인 정책을 적용하는 서비스

  등록된 DiscountPolicy Bean
  - fixedDiscount -> FixedDiscount
  - gradeDiscount -> GradeDiscount
  - percentDiscount -> PercentDiscount

  실행 결과
  - 주문 금액: 50000
  - 선택 정책: PercentDiscount
  - 할인 금액: 10000
  - 최종 금액: 40000

> - 구현체가 여러 개이므로 현재는 @Qualifier로 percentDiscount를 명시했다.
> - 다음 단계에서 생성자 주입 방식과 @Qualifier/@Primary 차이를 더 분리해서 관찰.

---
- [05-29 07:25] s2-3 · constructor vs field vs setter injection: <br>
> 실행 결과
> - 주문 금액: 50000
> - 생성자 주입 최종 금액: 40000
> - 필드 주입 최종 금액: 40000
> - 세터 주입 최종 금액: 40000

세 방식 모두 Spring 컨테이너 안에서는 주입이 가능하다.
필수 의존성인 DiscountPolicy에는 생성자 주입이 가장 명확하다.

  생성자 주입
  - final 필드를 사용할 수 있다.
  - 객체 생성 시 필수 의존성이 반드시 들어온다.
  - 테스트에서 new ConstructorInjectedOrder(fakePolicy) 형태로 직접 주입하기 쉽다.
  - 순환 참조가 있으면 생성 시점에 빠르게 드러난다.

  필드 주입
  - final 필드를 사용할 수 없다.
  - 객체 외부에서 의존성이 잘 보이지 않는다.
  - Spring 없이 단위 테스트를 만들기 어렵다.

  세터 주입
  - 선택 의존성에는 사용할 수 있다.
  - setter 호출 전까지 의존성이 비어 있을 수 있다.

---

- [05-29 07:56] s2-4 · Map<String, DiscountPolicy>: 
  주문 금액: 50000
  정책별 최종 금액
  - fixedDiscount: 40000
  - gradeDiscount: 42500
  - percentDiscount: 40000

  관찰
  - Map의 key는 Bean 이름이다.
  - @Component("percentDiscount")로 지정한 이름이 Map key가 된다.
  - 새 정책을 추가하면 Map에 자동으로 포함되므로 OCP를 설명하기 좋다.

---
- [05-29 08:28] s2-5 · @Primary vs @Qualifier: 
  주문 금액: 50000
  - @Primary 기본 선택 최종 금액: 40000
  - @Qualifier 명시 선택 최종 금액: 40000

  관찰
  - @Primary는 같은 타입 Bean이 여러 개일 때 기본 후보를 지정한다.
  - @Qualifier가 있으면 @Primary보다 @Qualifier가 우선한다.
  - @Primary는 기본값, @Qualifier는 명시 선택에 가깝다.

---
- [05-29 08:42] s3-1 · pure main: 객체 2개 직접 생성 / 최종 금액 40000 / 2ms
- [05-29 08:52] s3-1 · AnnotationConfigContext: Bean 11개 / 최종 금액 40000 / 1031ms
- [05-29 09:17] s3-1 · SpringApplication.run: Bean 208개 / 3648ms
- ---

- [05-29 09:44] s3-3 · singleton vs prototype: getBean 1000회 / singleton 생성 1회 / prototype 생성 1000회

---
- [05-29 09:59] s3-4 · @Lazy: eager boot 2139ms / lazy boot 31ms / lazy first getBean 1015ms


---
- [05-29 10:14] s4-1 · constructor circular reference: 
  결과: UnsatisfiedDependencyException

  관찰
  - OrderCircularService 생성에 DiscountCircularService가 필요하다.
  - DiscountCircularService 생성에 다시 OrderCircularService가 필요하다.
  - 생성자 주입은 객체 생성 전에 모든 필수 의존성이 필요하므로 순환 참조가 부팅 시점에 드러난다.
  - 해결 방향은 설계 분리, 중재자 도입, 또는 불가피한 경우 @Lazy 사용이다.

---
- [05-29 10:28] s4-3 · circular reference resolve: 
  @Lazy 해결
  - 한쪽 의존성을 지연 프록시로 주입해 부팅 시점의 즉시 생성 순환을 끊었다.
  - 단, 설계 결합이 사라진 것은 아니므로 임시 해결에 가깝다.

  중재자 분리 해결
  - OrderService와 DiscountService가 서로 직접 의존하지 않게 공통 협력 객체를 분리했다.
  - 순환 참조 원인을 구조적으로 제거한다.

  관찰
  - 권장 해결은 @Lazy보다 책임 분리다.
  - @Lazy는 프록시로 생성 시점을 늦추는 방식이고, 설계 결합 자체를 낮추지는 않는다.
