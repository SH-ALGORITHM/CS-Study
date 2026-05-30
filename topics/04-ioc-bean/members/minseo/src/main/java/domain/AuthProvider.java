package domain;

public interface AuthProvider {

    void authenticate(String id, String password, String token);
}
