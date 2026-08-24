package stage.s1;

import infra.MeasurementLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
@EnableAutoConfiguration
public class Stage1_1_HelloEvent {

    public record HelloEvent(String message) {}

    @Bean
    public HelloService helloService(ApplicationEventPublisher publisher) {
        return new HelloService(publisher);
    }

    @Bean
    public HelloListener helloListener() {
        return new HelloListener();
    }

    public static class HelloService {
        private final ApplicationEventPublisher publisher;
        public HelloService(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }
        public void sayHello(String name) {
            System.out.println("[publisher] sayHello — name=" + name
                + " thread=" + MeasurementLog.thread());
            publisher.publishEvent(new HelloEvent("hello " + name));
            System.out.println("[publisher] return — thread=" + MeasurementLog.thread());
        }
    }

    public static class HelloListener {
        @EventListener
        public void onHello(HelloEvent event) {
            System.out.println("[listener] received — message=" + event.message()
                + " thread=" + MeasurementLog.thread());
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage1_1_HelloEvent.class, args);
        MeasurementLog.title("STAGE 1-1 — publishEvent + @EventListener");
        ctx.getBean(HelloService.class).sayHello("world");
        ctx.close();
    }
}
