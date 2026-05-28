package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component("google")
public class GoogleAuthProvider implements AuthProvider{

    public GoogleAuthProvider() {
        System.out.println("[GoogleAuthProvider] 생성자 호출");
    }

    @PostConstruct
    public void init() {
        System.out.println("[GoogleAuthProvider] @PostConstruct");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[GoogleAuthProvider] @PreDestroy");
    }

    @Override
    public void authenticate(String id, String password, String token) {
        System.out.println("[Google] token: " + token);
    }
}
