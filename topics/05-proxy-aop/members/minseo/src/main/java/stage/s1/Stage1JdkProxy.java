package stage.s1;

import infra.MeasurementLog;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class Stage1JdkProxy {

    public interface Greeter {
        String greet(String name);
    }

    public static class GreeterImpl implements Greeter {
        @Override
        public String greet(String name) { return "hello " + name; }
    }

    public static void main(String[] args) {
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
    }
}
