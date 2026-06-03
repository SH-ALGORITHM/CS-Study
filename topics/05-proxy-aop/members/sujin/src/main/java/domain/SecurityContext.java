package domain;

public final class SecurityContext {
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();
    private SecurityContext() {}

    public static void setRole(String role) { CURRENT_ROLE.set(role); }
    public static String getRole()           { return CURRENT_ROLE.get(); }
    public static void clear()               { CURRENT_ROLE.remove(); }   // 스레드풀 오염 방지
}
