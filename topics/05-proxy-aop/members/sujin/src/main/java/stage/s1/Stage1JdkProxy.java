package stage.s1;

import infra.MeasurementLog;

import java.lang.reflect.Proxy;

public class Stage1JdkProxy {
    public static void main(String[] args) {
        AdminService real = new AdminServiceImpl();

        AdminService proxy = (AdminService) Proxy.newProxyInstance(
            AdminService.class.getClassLoader(),
            new Class[]{ AdminService.class },
            (p, method, methodArgs) -> {
                // 권한 검사가 끼어들 자리 (지금은 관찰용 로그만)
                System.out.println("[AUTH check] " + method.getName() + " 호출 전 권한 확인");
                    Object result = method.invoke(real, methodArgs);
                System.out.println("[AUTH pass]  " + method.getName() + " = " + result);
                return result;
            }
        );

        System.out.println("real.getClass()  = " + real.getClass().getName()); // stage.s1.AdminServiceImpl
        System.out.println("proxy.getClass() = " + proxy.getClass().getName()); // jdk.proxy1.$Proxy0
        proxy.deleteUser("admin01", "user42");

        MeasurementLog.save("s1", "JDK Dynamic Proxy — proxy.getClass() = "
            + proxy.getClass().getName());
    }
}
