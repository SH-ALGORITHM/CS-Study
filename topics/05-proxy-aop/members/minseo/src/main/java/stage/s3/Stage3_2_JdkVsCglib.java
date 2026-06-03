package stage.s3;

import infra.MeasurementLog;
import java.lang.reflect.Proxy;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

/**
 * STAGE 3-2 — JDK Dynamic Proxy vs CGLIB 1M 회 호출 시간 비교.
 */
public class Stage3_2_JdkVsCglib {

    public interface Worker {
        int work();
    }

    public static class WorkerImpl implements Worker {
        @Override
        public int work() { return 42; }
    }

    public static void main(String[] args) {
        // (1) 순수 호출 — 기준선
        Worker pure = new WorkerImpl();

        // (2) JDK Dynamic Proxy
        Worker jdkProxy = (Worker) Proxy.newProxyInstance(
            Worker.class.getClassLoader(),
            new Class<?>[]{Worker.class},
            (p, method, methodArgs) -> method.invoke(pure, methodArgs)
        );

        // (3) CGLIB Proxy
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(WorkerImpl.class);
        enhancer.setCallback((MethodInterceptor) (obj, method, methodArgs, methodProxy) ->
            methodProxy.invokeSuper(obj, methodArgs)
        );
        Worker cglibProxy = (Worker) enhancer.create();

        int warmup = 10_000;
        int iterations = 1_000_000;

        // JIT 웜업
        long sink = 0;
        for (int i = 0; i < warmup; i++) {
            sink += pure.work();
            sink += jdkProxy.work();
            sink += cglibProxy.work();
        }

        // 1. 순수 호출 측정
        long t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) sink += pure.work();
        long pureMs = (System.nanoTime() - t1) / 1_000_000;

        // 2. JDK Proxy 호출 측정
        long t2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) sink += jdkProxy.work();
        long jdkMs = (System.nanoTime() - t2) / 1_000_000;

        // 3. CGLIB Proxy 호출 측정
        long t3 = System.nanoTime();
        for (int i = 0; i < iterations; i++) sink += cglibProxy.work();
        long cglibMs = (System.nanoTime() - t3) / 1_000_000;

        MeasurementLog.title("STAGE 3-2 — JDK vs CGLIB 1M 회 호출 비교");
        MeasurementLog.row("순수 호출", pureMs + " ms");
        MeasurementLog.row("JDK Proxy", jdkMs + " ms");
        MeasurementLog.row("CGLIB Proxy", cglibMs + " ms");

        MeasurementLog.section("학습 포인트 (Java 21)");
        System.out.println("  · 최신 자바에서 두 방식의 성능 차이는 사실상 미미함");
        System.out.println("  · 스프링 부트가 CGLIB를 선호하는 이유는 성능 때문이 아니라,");
        System.out.println("    인터페이스 유무에 관계없이 일관된 프록시 생성 전략을 가져가기 위함");

        System.out.println("\n  (DCE 방지 sink check: " + sink + ")");
    }
}
