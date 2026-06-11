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
public class Stage4InventoryEvent {

    public static void main(
        String[] args
    ) {

        var ctx =
            SpringApplication.run(
                Stage4InventoryEvent.class,
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
            "[MAIN] Stage4 종료"
        );

        ctx.close();
    }
}
