package stage.s3;

import infra.MeasurementLog;
import java.lang.reflect.Proxy;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

public class Stage3_2_JdkVsCglib {

    public interface Worker {
        int work();
    }

    public static class WorkerImpl implements Worker {
        @Override
        public int work() {
            return 42;
        }
    }

    public static void main(String[] args) {
        Worker pure = new WorkerImpl();

        Worker jdkProxy = (Worker) Proxy.newProxyInstance(
            Worker.class.getClassLoader(),
            new Class<?>[]{Worker.class},
            (p, method, methodArgs) -> method.invoke(pure, methodArgs)
        );

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(WorkerImpl.class);
        enhancer.setCallback((MethodInterceptor) (obj, method, methodArgs, methodProxy) ->
            methodProxy.invokeSuper(obj, methodArgs)
        );
        Worker cglibProxy = (Worker) enhancer.create();

        int warmup = 10_000;
        int iterations = 1_000_000;

        long sink = 0;
        for (int i = 0; i < warmup; i++) {
            sink += pure.work();
            sink += jdkProxy.work();
            sink += cglibProxy.work();
        }

        long t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            sink += pure.work();
        }
        long pureMs = (System.nanoTime() - t1) / 1_000_000;

        long t2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            sink += jdkProxy.work();
        }
        long jdkMs = (System.nanoTime() - t2) / 1_000_000;

        long t3 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            sink += cglibProxy.work();
        }
        long cglibMs = (System.nanoTime() - t3) / 1_000_000;

        MeasurementLog.title("STAGE 3-2 — JDK vs CGLIB 1M 회 호출 비교");
        MeasurementLog.row("순수 호출", pureMs + " ms");
        MeasurementLog.row("JDK Proxy", jdkMs + " ms");
        MeasurementLog.row("CGLIB Proxy", cglibMs + " ms");
        MeasurementLog.row("(sink — DCE 방지)", sink);

        MeasurementLog.section("학습 포인트");
        System.out.println("  · Java 21 기준 JIT 웜업 후 런타임 호출 비용은 직접 측정값을 봐야 한다.");
        System.out.println("  · Spring Boot 2.0+ 기본 CGLIB는 성능 때문이 아니라 일관성과 타입 안전성 때문이다.");

        MeasurementLog.save("s3-2", "JDK vs CGLIB 1M 회",
            "pure=" + pureMs + "ms / jdk=" + jdkMs + "ms / cglib=" + cglibMs + "ms");
    }
}
