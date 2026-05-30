package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component("session")
public class SessionAuthProvider implements AuthProvider{

    public SessionAuthProvider() {
        System.out.println("[SessionAuthProvider] 생성자 호출");
    }

    @PostConstruct
    public void init() {
        System.out.println("[SessionAuthProvider] @PostConstruct");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[SessionAuthProvider] @PreDestroy");
    }

    @Override
    public void authenticate(String id, String password, String token) {
        System.out.println("[session] user id: " + id + ", password: " + password);
    }
}
