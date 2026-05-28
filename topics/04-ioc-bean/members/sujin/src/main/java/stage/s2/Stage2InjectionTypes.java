package stage.s2;

import domain.EmailSender;
import domain.NotificationSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * STAGE 2-3. 주입 방식 3가지 비교.
 *
 * 같은 의존성 (NotificationSender) 을 생성자 / 필드 / 세터 3가지 방식으로 받는 클래스를
 * 동일 컨테이너 안에 띄우고, 각 방식의 차이를 직접 관찰한다.
 *
 *  ┌──────────────┬──────────────┬──────────────┬────────────────────────────┐
 *  │ 방식          │ final 가능   │ 테스트 mock  │ 순환 참조 감지 시점            │
 *  ├──────────────┼──────────────┼──────────────┼────────────────────────────┤
 *  │ 생성자        │ O            │ 1줄          │ 부팅 시점 (즉시)             │
 *  │ 필드         │ X            │ 2~3줄 (리플) │ 런타임 (NPE 가능)             │
 *  │ 세터         │ X            │ 2줄          │ 런타임 또는 Boot 2.6+ 부팅실패 │
 *  └──────────────┴──────────────┴──────────────┴────────────────────────────┘
 *
 * AnnotationConfigApplicationContext 를 직접 사용해서 Spring Boot 자동 설정 노이즈 없이
 * 주입 방식 차이에만 집중한다.
 *
 * NOTE: EmailSender 가 클래스 자체에 @Component("email") 가 붙어 있지만,
 *   여기서는 @ComponentScan 을 켜지 않고 @Bean(name="email") 로 직접 등록한다.
 *   동일 인스턴스가 3 개 클래스에 주입되는지 검증하려는 의도.
 */
public class Stage2InjectionTypes {

    // ─────────────────────────────────────────────────────────
    // 1) 생성자 주입 — 권장
    //    - final 가능 → 불변, 멀티스레드 안전
    //    - 객체 생성 즉시 모든 의존성 보장 (NPE 위험 X)
    //    - 부팅 시점에 순환 참조 감지 (BeanCurrentlyInCreationException)
    //    - 테스트: new CtorInjected(mock) — 1줄
    // ─────────────────────────────────────────────────────────
    static class CtorInjected {
        private final NotificationSender sender;

        public CtorInjected(@Qualifier("email") NotificationSender sender) {
            System.out.println("[ctor] CtorInjected constructor — sender=" + sender.getClass().getSimpleName());
            this.sender = sender;
        }

        public NotificationSender sender() {
            return sender;
        }
    }

    // ─────────────────────────────────────────────────────────
    // 2) 필드 주입 — 비권장
    //    - final 불가 → 불변성 X
    //    - 리플렉션으로 주입 → IDE 가 "사용 안 한 필드" 처럼 보일 수 있음
    //    - 컨테이너 없이 new 만 하면 sender == null → NPE 위험
    //    - 테스트: ReflectionTestUtils.setField(target, "sender", mock) — 2~3줄
    // ─────────────────────────────────────────────────────────
    static class FieldInjected {
        @Autowired
        @Qualifier("email")
        private NotificationSender sender;

        public NotificationSender sender() {
            return sender;
        }
    }

    // ─────────────────────────────────────────────────────────
    // 3) 세터 주입 — 선택적 의존성에만 권장
    //    - final 불가
    //    - 객체 생성 후 setter 가 호출되기 전까지는 sender == null
    //    - 테스트: new SetterInjected(); target.setSender(mock); — 2줄
    //    - 선택적 (required=false) 의존성 표현에 적합
    // ─────────────────────────────────────────────────────────
    static class SetterInjected {
        private NotificationSender sender;

        @Autowired
        public void setSender(@Qualifier("email") NotificationSender sender) {
            System.out.println("[setter] SetterInjected.setSender — sender=" + sender.getClass().getSimpleName());
            this.sender = sender;
        }

        public NotificationSender sender() {
            return sender;
        }
    }

    @Configuration
    static class InjectionConfig {

        @Bean(name = "email")
        public NotificationSender emailSender() {
            return new EmailSender();
        }

        @Bean
        public CtorInjected ctorInjected(@Qualifier("email") NotificationSender sender) {
            return new CtorInjected(sender);
        }

        @Bean
        public FieldInjected fieldInjected() {
            return new FieldInjected();
        }

        @Bean
        public SetterInjected setterInjected() {
            return new SetterInjected();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== STAGE 2-3. 주입 방식 3가지 비교 ===");
        var context = new AnnotationConfigApplicationContext(InjectionConfig.class);

        var email = context.getBean("email", NotificationSender.class);
        var ctor = context.getBean(CtorInjected.class);
        var field = context.getBean(FieldInjected.class);
        var setter = context.getBean(SetterInjected.class);

        System.out.println();
        System.out.println("--- 같은 NotificationSender 가 3 곳에 주입됐는지 확인 (싱글톤 검증) ---");
        System.out.println("ctor.sender   == email Bean ? " + (ctor.sender() == email));
        System.out.println("field.sender  == email Bean ? " + (field.sender() == email));
        System.out.println("setter.sender == email Bean ? " + (setter.sender() == email));

        System.out.println();
        System.out.println("--- 컨테이너 없이 new 했을 때의 차이 (NPE 위험 시연) ---");
        try {
            var fieldNew = new FieldInjected();
            fieldNew.sender().send("x@x", "x");
            System.out.println("FieldInjected: 동작 (예상 외)");
        } catch (NullPointerException e) {
            System.out.println("FieldInjected: NPE 발생 — 컨테이너 없이 필드 주입이 안 되므로 sender == null");
        }
        try {
            var setterNew = new SetterInjected();
            setterNew.sender().send("x@x", "x");
            System.out.println("SetterInjected: 동작 (예상 외)");
        } catch (NullPointerException e) {
            System.out.println("SetterInjected: NPE 발생 — setSender() 호출 전에 sender == null");
        }
        var ctorNew = new CtorInjected(email);
        ctorNew.sender().send("x@x", "[ctorNew] 컨테이너 없이도 객체 생성 시점에 의존성 보장");

        System.out.println();
        System.out.println("--- mock 주입 코드 라인 비교 (테스트 시) ---");
        System.out.println("생성자: 1줄 → new CtorInjected(mock)");
        System.out.println("필드  : 2줄 → new FieldInjected(); ReflectionTestUtils.setField(t,\"sender\",mock)");
        System.out.println("세터  : 2줄 → new SetterInjected(); t.setSender(mock)");

        System.out.println();
        System.out.println("--- final 가능 여부 ---");
        System.out.println("생성자: O — private final NotificationSender sender;");
        System.out.println("필드  : X — final 필드는 리플렉션 주입 불가 (컴파일은 되지만 주입 안 됨)");
        System.out.println("세터  : X — setter 가 재할당하므로 final 불가");

        context.close();
    }
}
