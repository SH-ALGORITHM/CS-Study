package stage.s4;

import domain.AdminTaskService;
import domain.SecurityContext;
import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = "domain")
public class Stage4_SelfInvocation {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
            SpringApplication.run(Stage4_SelfInvocation.class, args);

        AdminTaskService svc = ctx.getBean(AdminTaskService.class);
        System.out.println("proxy class = " + svc.getClass().getName());

        SecurityContext.setRole("USER");            // 권한 없음 (ADMIN 아님)

        System.out.println("\n=== (1) 직접 호출 — 정상 차단 ===");
        String direct;
        try { direct = svc.deleteUser(1L); }
        catch (Exception e) { direct = "거부: " + e.getMessage(); }
        System.out.println("deleteUser(1)         → " + direct);

        System.out.println("\n=== (2) self-invocation 함정 — 우회됨 ===");
        String viaSelf;
        try { viaSelf = svc.deleteViaSelf(2L); }
        catch (Exception e) { viaSelf = "거부: " + e.getMessage(); }
        System.out.println("deleteViaSelf(2)      → " + viaSelf + "   ← 권한 검사 우회!");

        System.out.println("\n=== (3) self 프록시 주입으로 해결 ===");
        String fixed;
        try { fixed = svc.deleteViaSelfFixed(3L); }
        catch (Exception e) { fixed = "거부: " + e.getMessage(); }
        System.out.println("deleteViaSelfFixed(3) → " + fixed + "   ← 다시 차단됨");

        System.out.println("\n=== (4) final 메서드 — CGLIB 한계로 우회됨 ===");
        String viaFinal;
        try { viaFinal = svc.finalDelete(4L); }
        catch (Exception e) { viaFinal = "거부: " + e.getMessage(); }
        System.out.println("finalDelete(4)        → " + viaFinal + "   ← final 이라 우회!");

        SecurityContext.clear();
        MeasurementLog.save("s4", "self-invocation — 직접=" + direct + " / this경유=" + viaSelf
            + " / self프록시=" + fixed + " / final=" + viaFinal);
        ctx.close();
    }
}
