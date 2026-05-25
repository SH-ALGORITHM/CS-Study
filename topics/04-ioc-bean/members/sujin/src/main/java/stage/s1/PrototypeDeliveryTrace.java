package stage.s1;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PrototypeDeliveryTrace {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final int id;

    public PrototypeDeliveryTrace() {
        this.id = SEQUENCE.incrementAndGet();
        System.out.printf("[prototype] constructor id=%d%n", id);
    }

    @PostConstruct
    public void init() {
        System.out.printf("[prototype] @PostConstruct id=%d%n", id);
    }

    @PreDestroy
    public void destroy() {
        System.out.printf("[prototype] @PreDestroy id=%d - should not be called by Spring%n", id);
    }

    public int id() {
        return id;
    }
}
