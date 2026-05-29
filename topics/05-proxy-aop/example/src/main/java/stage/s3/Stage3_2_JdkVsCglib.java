package stage.s3;

import infra.MeasurementLog;
import java.lang.reflect.Proxy;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

/**
 * STAGE 3-2 — JDK Dynamic Proxy vs CGLIB 1M 회 호출 시간 비교.
 *
 * <p>Java 21 + JIT 웜업 후 두 방식의 차이는 사실상 무시할 만함.
 * Java 8 시절의 "리플렉션이 느리다" 통념은 더 이상 유효하지 않음.
 *
 * <p>예단하지 말고 본인 측정값을 그대로 기록.
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

        // sink 누적으로 DCE 방지
        long t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) sink += pure.work();
        long pureMs = (System.nanoTime() - t1) / 1_000_000;

        long t2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) sink += jdkProxy.work();
        long jdkMs = (System.nanoTime() - t2) / 1_000_000;

        long t3 = System.nanoTime();
        for (int i = 0; i < iterations; i++) sink += cglibProxy.work();
        long cglibMs = (System.nanoTime() - t3) / 1_000_000;

        MeasurementLog.title("STAGE 3-2 — JDK vs CGLIB 1M 회 호출 비교");
        MeasurementLog.row("순수 호출", pureMs + " ms");
        MeasurementLog.row("JDK Proxy", jdkMs + " ms");
        MeasurementLog.row("CGLIB Proxy", cglibMs + " ms");
        MeasurementLog.row("(sink — DCE 방지)", sink);

        MeasurementLog.section("학습 포인트 (Java 21 기준)");
        System.out.println("  · JIT 웜업 후 두 방식의 런타임 호출 비용은 사실상 동등");
        System.out.println("  · CGLIB 의 진짜 비용은 부팅 시 (바이트코드 생성) — 런타임 X");
        System.out.println("  · Spring Boot 2.0+ 가 CGLIB 기본 = 성능 아님 / 일관성 + 타입 안전성");
        System.out.println("  · 정확한 메커니즘 (코어 리플렉션 = Java 18 / C2 인라이닝 = 상시) 보다 본인 측정 신뢰");
    }
}
