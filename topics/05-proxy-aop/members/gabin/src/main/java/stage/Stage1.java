package stage;

import infra.MeasurementLog;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication(scanBasePackages = "stage")
public class Stage1 {

    public static void main(String[] args) {
        runJdkProxy();
        runCglibProxy();
        runSpringProxy();
    }

    private static void runJdkProxy() {
        Greeter real = new GreeterImpl();

        Greeter proxy = (Greeter) Proxy.newProxyInstance(
            Greeter.class.getClassLoader(),
            new Class<?>[]{Greeter.class},
            new InvocationHandler() {
                @Override
                public Object invoke(Object p, Method method, Object[] methodArgs) throws Throwable {
                    System.out.println("[before] " + method.getName());
                    Object result = method.invoke(real, methodArgs);
                    System.out.println("[after] " + method.getName() + " = " + result);
                    return result;
                }
            }
        );

        MeasurementLog.title("JDK Dynamic Proxy 손 작성");
        MeasurementLog.row("real.getClass()", MeasurementLog.classOf(real));
        MeasurementLog.row("proxy.getClass()", MeasurementLog.classOf(proxy));
        MeasurementLog.row("proxy instanceof Greeter", proxy instanceof Greeter);
        MeasurementLog.row("proxy instanceof GreeterImpl", proxy instanceof GreeterImpl);

        MeasurementLog.section("proxy.greet(\"world\") 호출");
        String result = proxy.greet("world");
        MeasurementLog.row("결과", result);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · proxy 는 Greeter 인터페이스만 구현 (GreeterImpl 상속 X)");
        System.out.println("  · 인터페이스 없는 클래스에는 적용 불가 — STAGE 1-2 CGLIB 가 필요한 이유");

        MeasurementLog.save(
            "s1",
            "JDK Dynamic Proxy 손 작성",
            "proxy=" + MeasurementLog.classOf(proxy)
                + " / GreeterImpl 상속 여부=" + (proxy instanceof GreeterImpl)
        );
    }

    private static void runCglibProxy() {
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

        MeasurementLog.save(
            "s1",
            "CGLIB Proxy 손 작성",
            "proxy=" + MeasurementLog.classOf(proxy)
                + " / Counter 상속 여부=" + (proxy instanceof Counter)
        );
    }

    private static void runSpringProxy() {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1.class);

        TxService txService = ctx.getBean(TxService.class);
        PlainService plainService = ctx.getBean(PlainService.class);

        MeasurementLog.title("Spring AOP 가 만든 프록시 확인");
        MeasurementLog.row("TxService (@Transactional 있음)", MeasurementLog.classOf(txService));
        MeasurementLog.row("PlainService (@Transactional 없음)", MeasurementLog.classOf(plainService));

        MeasurementLog.section("학습 포인트");
        System.out.println("  · @Transactional 있는 Bean → 프록시 (Spring 6: X$$SpringCGLIB$$0)");
        System.out.println("  · @Transactional 없는 Bean → 진짜 클래스 (프록시 X — 가로챌 advice 없으면 안 만듦)");
        System.out.println("  · Spring Boot 2.0+ 기본 = CGLIB");
        System.out.println("  · 정확한 접미사는 버전마다 다름. 직접 출력으로 확인이 본 스터디 콘셉트");

        MeasurementLog.section("캐싱 도메인 연결");
        System.out.println("  · @Cached도 @Transactional처럼 프록시/Aspect가 메서드 호출을 가로챈다.");
        System.out.println("  · cache hit 이면 실제 메서드를 실행하지 않고 바로 반환한다.");
        System.out.println("  · cache miss 이면 실제 메서드 실행 후 결과를 저장한다.");

        MeasurementLog.save(
            "s1",
            "Spring AOP 프록시 확인",
            "txService=" + MeasurementLog.classOf(txService)
                + " / plainService=" + MeasurementLog.classOf(plainService)
        );

        ctx.close();
    }

    public interface Greeter {
        String greet(String name);
    }

    public static class GreeterImpl implements Greeter {
        @Override
        public String greet(String name) {
            return "hello " + name;
        }
    }

    public static class Counter {
        private int n = 0;

        public int next() {
            return ++n;
        }
    }

    @org.springframework.stereotype.Service
    public static class TxService {
        @Transactional
        public String doWork() {
            return "tx";
        }
    }

    @org.springframework.stereotype.Service
    public static class PlainService {
        public String doWork() {
            return "plain";
        }
    }
}
