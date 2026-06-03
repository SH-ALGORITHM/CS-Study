package stage.s1;

import infra.MeasurementLog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
public class Stage1SpringProxy {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
            SpringApplication.run(Stage1SpringProxy.class, args);

        PlainService   plain   = ctx.getBean(PlainService.class);
        GuardedService guarded = ctx.getBean(GuardedService.class);

        String plainClass   = plain.getClass().getName();
        String guardedClass = guarded.getClass().getName();

        System.out.println("PlainService   (advice 없음)    = " + plainClass);
        System.out.println("GuardedService (@Transactional) = " + guardedClass);

        MeasurementLog.save("s1", "Spring getBean — PlainService(advice X) = " + plainClass);
        MeasurementLog.save("s1", "Spring getBean — GuardedService(@Transactional) = " + guardedClass);

            ctx.close();
    }

    @Service
    static class PlainService {              // 끼울 advice 없음 → 프록시 X
        public void run() { }
    }

    @Service
    static class GuardedService {
        @Transactional                       // 끼울 게 생김 → 프록시 O
        public void run() { }
    }
}
