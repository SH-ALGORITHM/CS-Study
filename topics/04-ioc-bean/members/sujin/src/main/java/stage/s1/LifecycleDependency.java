package stage.s1;

import org.springframework.stereotype.Component;

@Component
public class LifecycleDependency {

    public LifecycleDependency() {
        System.out.println("[lifecycle dependency] constructor");
    }

    public String name() {
        return "LifecycleDependency";
    }
}
