package stage.S1;

import infra.MeasurementLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
@ComponentScan(basePackageClasses = Stage1Injection.class)
public class Stage1Injection {

    public static void main(String[] args) {
        var ctx = new AnnotationConfigApplicationContext(Stage1Injection.class);

        System.out.println("=== [1-2] 등록 방식 비교 ===");
        // @Component로 등록된 빈
        System.out.println("ScanBean: " + ctx.getBean(ScanBean.class));
        // @Bean으로 직접 등록된 빈
        System.out.println("ManualBean: " + ctx.getBean(ManualBean.class));

        System.out.println("\n=== [1-3] 주입 방식 비교 ===");
        AuthService authService = ctx.getBean(AuthService.class);
        authService.checkStatus();

        ctx.close();

        MeasurementLog.save("s1", "Scan vs Bean & DI 관찰",
            "자동 스캔과 수동 등록의 차이, @Autowired를 통한 주입 확인");
    }

    // [방법 A] @Component: 내가 만든 클래스에 붙여서 "자동"으로 등록
    @Component
    static class ScanBean { }

    // [방법 B] @Bean: 외부 라이브러리 객체처럼 소스를 못 고치는 객체를 "수동"으로 등록
    @Bean
    public ManualBean manualBean() {
        return new ManualBean();
    }

    static class ManualBean { }

    // [의존성 주입 관찰] AuthService가 ScanBean을 "주입"받습니다.
    @Component
    static class AuthService {
        // @Autowired를 붙이면 스프링이 ctx에서 ScanBean을 찾아 꽂아줍니다.
        private final ScanBean scanBean;

        public AuthService(ScanBean scanBean) {
            this.scanBean = scanBean;
            System.out.println("  [DI] AuthService 생성자에서 ScanBean 주입 완료!");
        }

        public void checkStatus() {
            System.out.println("  [Service] ScanBean 존재 여부: " + (scanBean != null));
        }
    }
}
