package stage.S1;


import infra.MeasurementLog;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
 import org.springframework.stereotype.Component;

 @Configuration
 @ComponentScan(basePackageClasses = Stage1Lifecycle.class)
 public class Stage1Lifecycle {

     public static void main(String[] args) {
         System.out.println("=== [STAGE 1] 컨테이너 생성 시작 ===");
         var ctx = new AnnotationConfigApplicationContext(Stage1Lifecycle.class);

         System.out.println("\n--- [1] 싱글톤 빈 관찰 ---");
         // getBean 호출 전에도 이미 생성되어 있는지 확인해보세요
         NormalBean normal = ctx.getBean(NormalBean.class);
         System.out.println("NormalBean 사용 중...");

         System.out.println("\n--- [2] 프로토타입 빈 관찰 ---");
         System.out.println("ProtoBean 첫 번째 요청:");
         ctx.getBean(ProtoBean.class);
         System.out.println("ProtoBean 두 번째 요청:");
         ctx.getBean(ProtoBean.class);

         System.out.println("\n--- [4] 현재 등록된 전체 빈 개수 ---");
         System.out.println("Total Beans: " + ctx.getBeanDefinitionCount());
         for (String beanName : ctx.getBeanDefinitionNames()) {
             System.out.println(" - " + beanName);
         }

         System.out.println("\n--- [3] 컨테이너 종료 시작 ---");
         ctx.close();
         System.out.println("=== 컨테이너 종료 완료 ===\n");

         // 측정 결과 기록
         MeasurementLog.save("s1", "Bean 라이프사이클 및 개수",
             "싱글톤 소멸 확인, 프로토타입 미소멸 확인, 전체 빈 " + ctx.getBeanDefinitionCount() + "개 확인");
         }
     @Component
     static class NormalBean {
         public NormalBean() {
             System.out.println("  [Normal] 생성자 호출");
         }
         @PostConstruct
         public void init() {
             System.out.println("  [Normal] @PostConstruct 호출");
         }
         @PreDestroy
         public void destroy() {
             System.out.println("  [Normal] @PreDestroy 호출 (컨테이너가 종료를 관리함)");
         }
     }

     @Component
     @Scope("prototype")
     static class ProtoBean {
         public ProtoBean() {
             System.out.println("  [Proto] 생성자 호출");
         }
         @PostConstruct
         public void init() {
             System.out.println("  [Proto] @PostConstruct 호출");
         }
         @PreDestroy
         public void destroy() {

             System.out.println("  [Proto] @PreDestroy 호출 (과연 찍힐까요?)");
         }
     }
 }
