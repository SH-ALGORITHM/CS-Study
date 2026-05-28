package stage.S4;

import infra.MeasurementLog;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * STAGE 4-3: 순환 참조 해결 방법 2가지 (@Lazy vs 설계 변경)
 *
 * 앞선 Stage4Circular에서 터졌던 순환 참조 문제를 실제로 고쳐보는 실험입니다.
 */
public class Stage4Resolve {

    // =================================================================
    // 방법 1: @Lazy를 사용한 꼼수 회피 (당장 급할 때 불끄기 용도)
    // =================================================================
    @Component
    static class LazyA {
        private final LazyB b;
        // 핵심: 스프링에게 "B는 나중에 줄 테니까 일단 프록시(가짜 껍데기)라도 하나 줘!" 라고 부탁함
        public LazyA(@Lazy LazyB b) {
            System.out.println("  [LazyA] 생성자 — 스프링이 준 B의 정체는? " + b.getClass().getSimpleName());
            this.b = b;
        }
        public String hello() { return "A의 메서드!"; }
        public String callB() { return b.hello(); }
    }
    
    @Component
    static class LazyB {
        private final LazyA a;
        public LazyB(LazyA a) {
            System.out.println("  [LazyB] 생성자 — A 주입받음");
            this.a = a;
        }
        public String hello() { return "B의 메서드!"; }
    }


    // =================================================================
    // 방법 2: 공통 기능을 제3자에게 빼내는 설계 변경 (가장 권장되는 정석)
    // =================================================================
    
    // A와 B가 서로를 필요로 했던 진짜 이유(공통 로직)를 가진 제3의 클래스를 만듭니다.
    @Component
    static class CommonService {
        public String doSharedWork() { return "공통 서비스가 일합니다."; }
    }

    // 이제 A는 B를 부르지 않고, 제3자를 부릅니다.
    @Component
    static class CleanA {
        private final CommonService commonService;
        public CleanA(CommonService commonService) { this.commonService = commonService; }
        public String hello() { return "CleanA -> " + commonService.doSharedWork(); }
    }

    // B도 A를 부르지 않고, 제3자를 부릅니다. (서로 남남이 됨 = 순환 참조 끊어짐!)
    @Component
    static class CleanB {
        private final CommonService commonService;
        public CleanB(CommonService commonService) { this.commonService = commonService; }
        public String hello() { return "CleanB -> " + commonService.doSharedWork(); }
    }


    public static void main(String[] args) {
        System.out.println("=== 1. @Lazy를 사용한 순환 참조 회피 ===");
        var ctx1 = new AnnotationConfigApplicationContext();
        ctx1.register(LazyA.class, LazyB.class);
        ctx1.refresh(); // 에러 안 나고 통과!
        System.out.println("  부팅 성공! @Lazy가 닭-달걀 문제를 회피하게 해줌.");
        
        LazyA a = ctx1.getBean(LazyA.class);
        System.out.println("  실제로 B를 호출해볼까? -> " + a.callB());
        ctx1.close();


        System.out.println("\n===============================================");


        System.out.println("\n=== 2. 설계 변경(제3자 분리)을 통한 근본적 해결 ===");
        var ctx2 = new AnnotationConfigApplicationContext();
        ctx2.register(CommonService.class, CleanA.class, CleanB.class);
        ctx2.refresh(); // 당연히 에러 안 남
        System.out.println("  부팅 성공! 서로 쳐다보지도 않음.");
        
        CleanA cleanA = ctx2.getBean(CleanA.class);
        CleanB cleanB = ctx2.getBean(CleanB.class);
        System.out.println("  " + cleanA.hello());
        System.out.println("  " + cleanB.hello());
        ctx2.close();

        MeasurementLog.save("s4-3", "@Lazy로 순환참조 회피", "성공 - 프록시(가짜) 객체를 대신 주입받아 통과함");
        MeasurementLog.save("s4-3", "설계 변경으로 근본 해결", "성공 - 공통 기능을 분리하여 A와 B의 양방향 의존을 완벽히 끊어냄");

        System.out.println("\n[학습 포인트]");
        System.out.println("  - @Lazy 해결법: 당장 에러는 잡지만 코드가 지저분해집니다 (스프링이 가짜 객체를 끼워넣음). 실무에서 장애가 났을 때 급한 불을 끄는 용도입니다.");
        System.out.println("  - 재설계 해결법: 시간이 좀 걸리더라도, 결국 A와 B의 공통 로직을 떼어내어 한 방향으로만 흐르게 구조를 고치는 것이 유일한 정답입니다.");
    }
}