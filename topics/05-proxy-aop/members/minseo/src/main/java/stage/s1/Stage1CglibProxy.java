package stage.s1;

import infra.MeasurementLog;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

public class Stage1CglibProxy {

    /** 인터페이스 없음. CGLIB 만 적용 가능. */
    public static class Counter {
        private int n = 0;
        public int next() { return ++n; }
    }

    public static void main(String[] args) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(Counter.class);
        enhancer.setCallback((MethodInterceptor) (obj, method, methodArgs, methodProxy) -> {
            System.out.println("[before] " + method.getName());
            Object result = methodProxy.invokeSuper(obj, methodArgs);
            System.out.println("[after] " + method.getName() + " = " + result);
            return result;
        });

        Counter proxy = (Counter) enhancer.create();

        MeasurementLog.title("CGLIB Proxy 손 작성");
        MeasurementLog.row("proxy.getClass()", MeasurementLog.classOf(proxy));
        MeasurementLog.row("proxy instanceof Counter", proxy instanceof Counter);

        MeasurementLog.section("proxy.next() 3 회 호출");
        proxy.next();
        proxy.next();
        proxy.next();

        MeasurementLog.section("학습 포인트");
        System.out.println("  · proxy 가 Counter 의 자식 클래스로 동적 생성됨 (바이트코드 조작)");
        System.out.println("  · 인터페이스 없어도 OK. 단 final 클래스 / final 메서드 / private 메서드 한계");
        System.out.println("  · Spring AOP 의 기본 (Spring Boot 2.0+) — STAGE 1-3 에서 확인");
    }
}
