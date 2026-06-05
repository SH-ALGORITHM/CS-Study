package domain;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class AdminTaskService {

    @Lazy
    @Autowired
    private AdminTaskService self;                 // 자기 자신(프록시) 주입 — 해결용

    @RequireRole("ADMIN")
    public String deleteUser(long targetUserId) {
        return "user " + targetUserId + " 삭제 완료";
    }

    public String viewUser(long targetUserId) {     // @RequireRole 없음
        return "user " + targetUserId + " 조회";
    }

    // (1) 함정: this.deleteUser() → 프록시 우회 → @RequireRole 안 먹음
    public String deleteViaSelf(long targetUserId) {
        return this.deleteUser(targetUserId);
    }

    // (2) 해결: 주입받은 프록시(self) 경유 → @RequireRole 다시 작동
    public String deleteViaSelfFixed(long targetUserId) {
        return self.deleteUser(targetUserId);
    }

    // (3) CGLIB 한계: final 메서드는 프록시가 오버라이드 못 함 → @RequireRole 무력화
    @RequireRole("ADMIN")
    public final String finalDelete(long targetUserId) {
        return "user " + targetUserId + " (final) 삭제";
    }
}
