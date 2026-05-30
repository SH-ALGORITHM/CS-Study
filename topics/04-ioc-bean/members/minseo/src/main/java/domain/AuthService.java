package domain;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthProvider provider;
    private final AuthRepository repository;

    public AuthService(@Qualifier("jwt") AuthProvider provider, AuthRepository repository) {
        System.out.println("[AuthService] 생성자 — 주입된 provider: "
            + provider.getClass().getSimpleName());
        this.provider = provider;
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        System.out.println("[AuthService] @PostConstruct");
    }

    public void login(String id, String password, String token) {
        provider.authenticate(id, password, token);
        System.out.println("[AuthService] DB에 인증 로그 저장 완료 (AuthRepository 연동)");
    }
}
