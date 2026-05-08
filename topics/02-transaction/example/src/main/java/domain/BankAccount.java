package domain;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 2주차 예시 도메인 — 은행 계좌 (DB 버전).
 *
 * <h3>1주차 BankAccount 와 무엇이 같고 다른가</h3>
 * <table border="1">
 *   <tr><th></th><th>1주차</th><th>2주차</th></tr>
 *   <tr><td>잔고 위치</td><td>JVM 메모리 ({@code int balance})</td><td>DB 컬럼 ({@code account.balance})</td></tr>
 *   <tr><td>race 발생 지점</td><td>{@code if (balance &gt;= amount) balance -= amount}</td><td>SELECT → 앱 계산 → UPDATE 절대값</td></tr>
 *   <tr><td>학습 포인트</td><td>race condition 자체</td><td>트랜잭션/격리 수준이 race 를 어떻게 막거나 못 막는가</td></tr>
 * </table>
 *
 * <h3>왜 read-modify-write 패턴으로 짰는가 (의도)</h3>
 * <pre>
 *   1) SELECT balance FROM account WHERE id = ?       — 현재 잔고 읽음
 *   2) (앱 메모리에서) newBalance = balance - amount   — 새 값 계산
 *   3) UPDATE account SET balance = ? WHERE id = ?    — 절대값으로 덮어씀
 * </pre>
 *
 * 1) 과 3) 사이에 다른 트랜잭션이 끼어들면 Lost Update 발생.
 * 1주차 {@code count++} 가 망가진 것과 정확히 같은 구조.
 *
 * 만약 한 줄 atomic UPDATE 로 짠다면 (예: {@code UPDATE ... SET balance = balance - ?}),
 * PG 의 row-level lock 이 자동으로 막아 Lost Update 가 보이지 않는다.
 * 2주차 학습 포인트 (격리 수준이 RMW 를 막는가) 를 보려면 의도적으로 RMW 로 짜야 한다.
 *
 * <h3>STAGE 별 사용법</h3>
 * <ul>
 *   <li>STAGE 2-1: {@code stage.Stage2RaceJdbc} 가 직접 트랜잭션을 다루며 호출</li>
 *   <li>STAGE 2-2: {@code stage.Stage2WithHelper} 가 {@code infra.TransactionHelper} 안에서 호출</li>
 *   <li>STAGE 3: {@code stage.Stage3Measurement} 가 격리 수준 4 단계로 측정</li>
 * </ul>
 */
public class BankAccount {

    private final long id;

    public BankAccount(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    /**
     * 잔고에서 amount 만큼 출금. read-modify-write 패턴 (의도적으로 race 발생 가능).
     *
     * <p>호출자 책임: {@code conn.setAutoCommit(false)} 가 이미 호출되어 있고,
     * 이 메서드 호출 후 {@code conn.commit()} 또는 {@code rollback()} 을 직접 다룰 것.
     * (트랜잭션 경계는 의도적으로 Stage 코드 / TransactionHelper 가 담당)
     *
     * @return 출금 성공 여부 (잔고 부족이면 false)
     */
    public boolean withdraw(Connection conn, long amount) throws SQLException {
        // [1] 현재 잔고 SELECT
        long currentBalance;
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT balance FROM account WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                currentBalance = rs.getLong("balance");
            }
        }

        // [2] 잔고 부족 체크
        if (currentBalance < amount) {
            return false;
        }

        // [3] 앱 메모리에서 새 잔고 계산
        long newBalance = currentBalance - amount;

        // ⚠️ [1] 과 [4] 사이가 race 발생 위험 구간.
        //    다른 트랜잭션이 이 사이에 [1]~[4] 를 다 끝내면 우리 [4] 가 그 변경을 덮어씀.

        // [4] 새 잔고로 UPDATE (절대값)
        // 참고: 현재는 PK 접근이라 영향 row 수가 항상 1. 일반화된 도메인이라면
        // executeUpdate() 반환값이 0 인 경우 (조건 불일치 / row 사라짐) 도 처리해야 한다.
        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE account SET balance = ? WHERE id = ?")) {
            ps.setLong(1, newBalance);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
        return true;
    }

    /**
     * 현재 잔고 조회 — 측정 종료 후 결과 확인용.
     */
    public long getBalance(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT balance FROM account WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("balance");
            }
        }
    }
}
