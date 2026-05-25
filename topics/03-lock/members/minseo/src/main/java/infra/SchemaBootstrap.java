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

    public static void resetAlls(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS seat (
                  id SERIAL PRIMARY KEY,
                  concert_name VARCHAR(100) NOT NULL,
                  seat_no INT NOT NULL,
                  reserved_by VARCHAR(50) DEFAULT NULL,
                  version BIGINT NOT NULL DEFAULT 0
                )
                """);

            s.execute("""
                CREATE TABLE IF NOT EXISTS user_wallet (
                  user_id VARCHAR(50) PRIMARY KEY,
                  balance NUMERIC NOT NULL DEFAULT 100000,
                  version BIGINT NOT NULL DEFAULT 0
                )
                """);

            s.execute("TRUNCATE seat, user_wallet RESTART IDENTITY CASCADE");

            s.execute("""
                INSERT INTO seat (id, concert_name, seat_no) VALUES (1, 'TWICE Concert', 1), (2, 'TWICE Concert', 2)
                """);

            s.execute("""
                INSERT INTO user_wallet (user_id, balance) VALUES ('UserA', 100000), ('UserB', 100000)
                """);
        }
    }

}
