package stage.s1;

public class AdminServiceImpl implements AdminService {
    @Override
    public String deleteUser(String adminId, String targetUserId) {
        return targetUserId + " 삭제 완료 (by " + adminId + ")";
    }
}
