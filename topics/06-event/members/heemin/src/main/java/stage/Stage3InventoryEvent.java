package stage;

import domain.InventoryService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    scanBasePackages = {
        "domain",
        "infra"
    }
)
public class Stage3InventoryEvent {

    public static void main(
        String[] args
    ) {

        var ctx =
            SpringApplication.run(
                Stage3InventoryEvent.class,
                args
            );

        InventoryService service =
            ctx.getBean(
                InventoryService.class
            );

        service.decrease(
            1L,
            10
        );

        System.out.println(
            "[MAIN] 정상 종료"
        );

        ctx.close();
    }
}
