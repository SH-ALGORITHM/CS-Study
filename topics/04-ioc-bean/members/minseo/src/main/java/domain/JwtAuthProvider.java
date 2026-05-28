package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component("jwt")
@Primary
public class JwtAuthProvider implements AuthProvider {

    public JwtAuthProvider() {
        System.out.println("[JwtAuthProvider] 생성자 호출");
    }

    @PostConstruct
    public void init() {
        System.out.println("[JwtAuthProvider] @PostConstruct");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[JwtAuthProvider] @PreDestroy");
    }

    @Override
    public void authenticate(String id, String password, String token) {
        System.out.println("[Jwt] token: " + token);
    }
}
