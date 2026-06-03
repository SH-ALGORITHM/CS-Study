package domain;

import org.springframework.stereotype.Service;

@Service
public class RoleService {

    private final RoleRepository repo;
    public RoleService(RoleRepository repo) { this.repo = repo; }

    @MyTransactional
    @RequireRole("ADMIN")           // 호출자가 ADMIN 이어야 권한 부여 가능
    public void grantRole(long userId, String role, boolean failAudit) {
        repo.insertRole(userId, role);                       // 권한 부여
        if (failAudit) throw new RuntimeException("감사 로그 기록 실패");
        repo.insertLog(userId, role);                        // 감사 로그
    }
}
