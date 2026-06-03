package stage.s1;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LifecycleSampleBean {

    private LifecycleDependency dependency;

    public LifecycleSampleBean() {
        System.out.println("[lifecycle sample] [1] constructor");
        System.out.println("[lifecycle sample]     dependency in constructor = " + dependency);
    }

    @Autowired
    public void setDependency(LifecycleDependency dependency) {
        System.out.println("[lifecycle sample] [2] setter injection");
        this.dependency = dependency;
    }

    @PostConstruct
    public void init() {
        System.out.println("[lifecycle sample] [3] @PostConstruct, dependency = "
            + dependency.name());
    }

    public void use() {
        System.out.println("[lifecycle sample] [use] business method");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[lifecycle sample] [4] @PreDestroy");
    }
}
