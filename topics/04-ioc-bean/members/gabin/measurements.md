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

