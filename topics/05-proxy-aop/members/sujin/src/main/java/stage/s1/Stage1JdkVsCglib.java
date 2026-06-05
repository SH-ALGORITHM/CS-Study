package stage.s1;

import infra.MeasurementLog;
import java.lang.reflect.Proxy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
public class Stage1JdkVsCglib {

    public interface AccountService {            // ★ 인터페이스 있음
        void withdraw(long id, long amount);
    }

    @Service
    public static class AccountServiceImpl implements AccountService {
        @Transactional
        @Override
        public void withdraw(long id, long amount) { }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
            SpringApplication.run(Stage1JdkVsCglib.class, args);

        // 반드시 인터페이스 타입으로 조회 (JDK 프록시는 impl 타입으로 못 받음)
        AccountService svc = ctx.getBean(AccountService.class);
        String cls = svc.getClass().getName();
        boolean isJdkProxy = Proxy.isProxyClass(svc.getClass());

        System.out.println("proxy class = " + cls);
        System.out.println("JDK proxy?  = " + isJdkProxy);

        MeasurementLog.save("s1", "인터페이스 있음 + @Transactional → " + cls
            + " (JDK proxy=" + isJdkProxy + ")");
        ctx.close();
    }
}
