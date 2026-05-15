package infra;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 계좌 이체 도메인의 측정 전 상태 리셋 유틸.
 *
 * <h3>3 주차 추가 컬럼</h3>
 * {@code version BIGINT NOT NULL DEFAULT 0} — 낙관적 락용. 매 UPDATE 마다 +1.
 *
 * <h3>왜 매번 리셋하나</h3>
 * 1, 2 주차 측정 원칙과 동일 — 이전 측정 잔여 상태가 다음 측정에 영향을 주면
 * 결과 해석이 어려워진다. {@code TRUNCATE} + 시드로 명시적 초기화.
 *
 * <h3>두 row (id=1, id=2) 시드 이유</h3>
 * 데드락 학습용. 한 row 만 있으면 다른 순서로 락 잡기가 불가능 → 데드락 발생 X.
 */
public final class SchemaBootstrap {

    private SchemaBootstrap() {}

    /**
     * 측정 시작 / 사이 호출 — 계좌 도메인을 깨끗한 상태로 복원.
     *
     * <ol>
     *   <li>{@code account} 테이블 존재 보장 (없으면 생성)</li>
     *   <li>두 계좌 시드 (id=1, id=2, balance=10000, version=0)</li>
     *   <li>이미 있으면 balance / version 초기값으로 UPDATE</li>
     * </ol>
     */
    public static void resetAccounts(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS account (
                    id BIGINT PRIMARY KEY,
                    balance NUMERIC NOT NULL,
                    version BIGINT NOT NULL DEFAULT 0
                )
                """);

            s.execute("""
                INSERT INTO account (id, balance) VALUES (1, 10000), (2, 10000)
                ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance, version = 0
                """);
        }
    }
}
