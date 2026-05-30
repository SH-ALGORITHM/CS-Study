package domain;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component("kakao")
public class KakaoAuthProvider implements AuthProvider{

    public KakaoAuthProvider() {
        System.out.println("[KakaoAuthProvider] 생성자 호출");
    }

    @PostConstruct
    public void init() {
        System.out.println("[KakaoAuthProvider] @PostConstruct");
    }

    @PreDestroy
    public void destroy() {
        System.out.println("[KakaoAuthProvider] @PreDestroy");
    }

    @Override
    public void authenticate(String id, String password, String token) {
        System.out.println("[Kakao] token: " + token);
    }
}



