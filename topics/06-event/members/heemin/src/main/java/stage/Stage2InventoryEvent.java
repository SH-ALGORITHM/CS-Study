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
public class Stage2InventoryEvent {

    public static void main(String[] args) {

        var ctx =
            SpringApplication.run(
                Stage2InventoryEvent.class,
                args
            );

        InventoryService service =
            ctx.getBean(InventoryService.class);

        try {
            service.decrease(1L, 10);
        } catch (Exception e) {
            System.out.println(
                "[MAIN] 예외 발생"
            );
        }

        ctx.close();
    }
}
