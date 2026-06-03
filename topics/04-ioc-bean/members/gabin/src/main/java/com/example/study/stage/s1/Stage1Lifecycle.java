package com.example.study.stage.s1;

import com.example.study.MeasurementLog;
import com.example.study.sample.PrototypeBean;
import com.example.study.sample.SampleBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Stage1Lifecycle {

    public static void main(String[] args) {
        long start = System.nanoTime();

        System.out.println("=== 싱글톤 Bean 라이프사이클 ===");
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(SampleBean.class);
        ctx.refresh();

        SampleBean bean = ctx.getBean(SampleBean.class);
        System.out.println("  [3] getBean() 후 사용 — " + bean.getClass().getSimpleName());

        ctx.close();
        System.out.println("싱글톤: close() 호출 시 @PreDestroy 호출 확인\n");

        System.out.println("=== 프로토타입 Bean 라이프사이클 ===");
        AnnotationConfigApplicationContext ctx2 = new AnnotationConfigApplicationContext();
        ctx2.register(PrototypeBean.class);
        ctx2.refresh();

        PrototypeBean p1 = ctx2.getBean(PrototypeBean.class);
        PrototypeBean p2 = ctx2.getBean(PrototypeBean.class);
        System.out.println("  p1 == p2 ? " + (p1 == p2) + "  (프로토타입은 매번 새 인스턴스)");

        ctx2.close();
        System.out.println("프로토타입: close() 호출 후에도 @PreDestroy 안 찍힘");
        System.out.println("→ 컨테이너는 프로토타입의 생성까지만 책임짐. 소멸은 GC 가 처리.");

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        MeasurementLog.save(
            "s1-1",
            "Bean lifecycle",
            String.join(System.lineSeparator(),
                "",
                "  싱글톤 Bean 라이프사이클",
                "  [1] SampleBean 생성자 호출",
                "  [2] SampleBean @PostConstruct",
                "  [3] getBean() 후 사용 - " + bean.getClass().getSimpleName(),
                "  [4] SampleBean @PreDestroy",
                "",
                "  프로토타입 Bean 라이프사이클",
                "  [1] PrototypeBean 생성자 호출",
                "  [2] PrototypeBean @PostConstruct",
                "  [3] getBean() 두 번 호출",
                "  p1 == p2 ? " + (p1 == p2),
                "  @PreDestroy 호출 여부: 호출 안 됨",
                "",
                "  관찰: singleton은 컨테이너 종료 시 소멸 콜백까지 관리하고, prototype은 생성까지만 관리한다.",
                "  실행 시간: " + elapsedMs + "ms"
            )
        );
    }
}
