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
public class Stage1InventoryEvent {

    public static void main(String[] args) {

        var ctx =
            SpringApplication.run(
                Stage1InventoryEvent.class,
                args
            );

        InventoryService service =
            ctx.getBean(InventoryService.class);

        System.out.println();
        System.out.println(
            "=== STAGE 1 - publishEvent + @EventListener ==="
        );
        System.out.println();

        service.decrease(1L, 10);

        ctx.close();
    }
}
