package stage.s3;

import infra.MeasurementLog;
import java.lang.reflect.Proxy;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

public class Stage3_1_ProxySpeed {

    public interface Task { int run(); }
    public static class TaskImpl implements Task { public int run() { return 1; } }
    public static class PlainTask         { public int run() { return 1; } }   // CGLIB용(무인터페이스)

    static final int WARMUP = 10_000, CALLS = 1_000_000, ROUNDS = 5;
    interface Op { int call(); }

    public static void main(String[] args) {
        Task real = new TaskImpl();

        Task jdk = (Task) Proxy.newProxyInstance(
            Task.class.getClassLoader(), new Class[]{Task.class},
            (p, m, a) -> m.invoke(real, a));               // no-op 핸들러

        Enhancer e = new Enhancer();
        e.setSuperclass(PlainTask.class);
        e.setCallback((MethodInterceptor) (o, m, a, mp) -> mp.invokeSuper(o, a));
        PlainTask cglib = (PlainTask) e.create();

        long pure  = bench(real::run);
        long jdkT  = bench(jdk::run);
        long cgT   = bench(cglib::run);

        MeasurementLog.title("STAGE 3 — 1M회 호출 (min of " + ROUNDS + ", 웜업 " + WARMUP + ")");
        System.out.printf("  순수 호출   = %d ms%n", pure);
        System.out.printf("  JDK Proxy   = %d ms%n", jdkT);
        System.out.printf("  CGLIB Proxy = %d ms%n", cgT);

        MeasurementLog.save("s3", "1M회 호출 — 순수=" + pure + "ms / JDK=" + jdkT + "ms / CGLIB=" + cgT +
            "ms (min/" + ROUNDS + ")");
    }

    static long bench(Op op) {
        for (int i = 0; i < WARMUP; i++) op.call();            // JIT 웜업
        long best = Long.MAX_VALUE;
        for (int r = 0; r < ROUNDS; r++) {
            long t = System.nanoTime();
            int sink = 0;
            for (int i = 0; i < CALLS; i++) sink += op.call();
            long ms = (System.nanoTime() - t) / 1_000_000;
            if (sink == Integer.MIN_VALUE) System.out.print("");   // DCE 방지
            best = Math.min(best, ms);
        }
        return best;
    }
}
