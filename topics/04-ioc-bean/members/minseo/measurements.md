# 측정 기록

자동 누적. 옆에 해석 메모는 직접 추가하세요.

- [05-26 03:29] s1 · Bean 라이프사이클: 싱글톤은 close 시 @PreDestroy 호출되나, 프로토타입은 호출 안 됨 확인
1. 싱글톤은 "미리" 만들어진다 (Eager Initialization)
   --- [1] 싱글톤 빈 관찰 --- 로그가 찍히기도 전에 NormalBean의 생성자가 호출된 거 보이시죠?
* 의미: 스프링 컨테이너는 부팅될 때 특별한 설정이 없다면 모든 싱글톤 빈을 미리 다 만들어 둡니다. 그래서 실제 쓸 때는
  이미 준비된 객체를 바로 꺼내 쓰기만 하면 돼요.

2. 프로토타입은 "요청할 때" 만들어진다 (Lazy)
   ProtoBean 첫 번째 요청: 로그 이후에 생성자 로그가 찍혔죠?
* 의미: 프로토타입 빈은 스프링이 미리 안 만들어둡니다. 누군가 "꺼내 줘!(getBean)"라고 할 때만 새로 생성해서
  던져줍니다.

3. 프로토타입의 소멸(@PreDestroy)은 스프링 책임이 아니다
   ctx.close()를 했을 때 NormalBean은 인사를 했지만, ProtoBean은 침묵했습니다.
* 의미: 스프링 컨테이너는 프로토타입 빈을 "생성하고 주입해 주는 것"까지만 책임집니다. 그 이후에 이 객체를 버릴지
  말지는 꺼내 간 사람(클라이언트 코드)의 책임이에요. 그래서 스프링이 죽을 때 프로토타입 빈의 소멸 메서드를 대신
  호출해 주지 않습니다.
- [05-26 03:39] s1 · Bean 라이프사이클 및 개수: 싱글톤 소멸 확인, 프로토타입 미소멸 확인, 전체 빈 9개 확인
  🔍 잠깐, 이 일꾼들은 누구일까요? (중요!)
  리스트를 자세히 보면 아주 흥미로운 이름들이 있습니다:
  1. internalAutowiredAnnotationProcessor: 우리가 코드에 @Autowired를 붙였을 때, 그게 누군지 찾아내서 실제로 꽂아주는
     역할을 하는 빈입니다. (얘가 없으면 @Autowired는 작동 안 해요!)
  2. internalCommonAnnotationProcessor: @PostConstruct, @PreDestroy 같은 표준 어노테이션을 인식해서 오늘 우리가 본
     로그를 찍게 해주는 빈입니다.
- [05-26 03:49] s1 · Scan vs Bean & DI 관찰: 자동 스캔과 수동 등록의 차이, @Autowired를 통한 주입 확인
  1. @Component vs @Bean: @Component는 내 코드, @Bean은 외부 라이브러리(수정 불가) 객체 등록에 사용.
  2. 생성자 주입: 생성자가 하나일 땐 @Autowired 생략 가능(스프링 4.3+). 객체를 new로 직접 조립하지 않아도 스프링이 의존성을 꽂아줌.
- [05-26 04:00] s1 · Spring Boot 빈 개수 측정: 전체 빈 283개 확인. @SpringBootApplication이 가져오는 자동 설정(Tomcat, JPA, DB Pool 등)의 규모를 체감함. devtools에 의한 재시작으로 로그가 중복 출력됨을 확인.
- [05-26 04:15] s2-1 · 3주차 코드 마이그레이션 (Before vs After): 직접 팩토리 관리 방식에서 스프링 @Configuration & @Bean 방식으로 전환 완료
  1. 객체 생성 및 소멸 책임 전가: Before에서는 직접 close()를 호출했으나, After에서는 ctx.close()가 @Bean(destroyMethod="close")를 통해 자동으로 자원을 정리함.
  2. 자동 의존성 주입 확인: AuthRepository 생성자에 별도의 조립 코드 없이도 DataSource가 정상적으로 주입됨(repository injected DataSource type 확인).
  3. 설정 분리: DataSourceConfig, RedisConfig로 설정을 분리하고 @Import를 통해 관리하는 구조로 개선.
