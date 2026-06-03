package stage.s1;

// import 는 Spring 내장 — net.sf.cglib 직접 추가하면 충돌
import infra.MeasurementLog;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

public class Stage1CglibProxy {
    public static void main(String[] args) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(ReportService.class);
        enhancer.setCallback((MethodInterceptor) (obj, method, methodArgs, methodProxy) -> {
            System.out.println("[AUTH check] " + method.getName());
            Object result = methodProxy.invokeSuper(obj, methodArgs);
            System.out.println("[AUTH pass]  " + method.getName() + " = " + result);
            return result;
        });

        ReportService proxy = (ReportService) enhancer.create();
        System.out.println("proxy.getClass()        = " + proxy.getClass().getName());
        System.out.println("instanceof ReportService = " + (proxy instanceof ReportService));
        proxy.generateMonthlyReport();

        MeasurementLog.save("s1", "CGLIB Proxy — getClass() = "
            + proxy.getClass().getName()
            + " / instanceof ReportService = " + (proxy instanceof ReportService));
    }
}
