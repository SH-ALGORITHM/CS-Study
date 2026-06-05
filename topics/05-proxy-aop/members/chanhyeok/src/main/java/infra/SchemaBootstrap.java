package infra;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * P2P 송금 도메인 — 측정 전 상태 리셋 유틸.
 *
 * <h3>왜 매번 리셋하나</h3>
 * 1, 2 주차 측정 원칙과 동일 — 이전 측정 잔여 상태가 다음 측정에 영향을 주면
 * 결과 해석이 어려워진다. 명시적 초기화 + 시드.
 *
 * <h3>리셋 대상</h3>
 * <ul>
 *   <li>user_wallet — 두 사용자 row (id=1, id=2). balance / daily_sent_amount / version 초기화</li>
 *   <li>fee_revenue — 단일 row (id=1). total_collected / version 초기화</li>
 *   <li>transfer_log — TRUNCATE (이전 측정 이력 제거)</li>
 * </ul>
 *
 * <h3>row 구성 이유</h3>
 * user_wallet 2 개 → 데드락 학습 (양방향 송금에서 락 순서 깸).
 * fee_revenue 1 개 → 핫스팟 학습 (모든 송금이 한 row 에 몰림).
 */
public final class SchemaBootstrap {

    private SchemaBootstrap() {}

    public static void reset(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {

            s.execute("""
                CREATE TABLE IF NOT EXISTS user_wallet (
                    id BIGINT PRIMARY KEY,
                    balance NUMERIC NOT NULL,
                    daily_sent_amount NUMERIC NOT NULL DEFAULT 0,
                    version BIGINT NOT NULL DEFAULT 0
                )
                """);

            s.execute("""
                CREATE TABLE IF NOT EXISTS fee_revenue (
                    id BIGINT PRIMARY KEY,
                    total_collected NUMERIC NOT NULL,
                    version BIGINT NOT NULL DEFAULT 0
                )
                """);

            s.execute("""
                CREATE TABLE IF NOT EXISTS transfer_log (
                    id BIGSERIAL PRIMARY KEY,
                    from_id BIGINT NOT NULL,
                    to_id BIGINT NOT NULL,
                    amount NUMERIC NOT NULL,
                    fee NUMERIC NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);

            // 시드 + 리셋
            s.execute("""
                INSERT INTO user_wallet (id, balance, daily_sent_amount, version)
                VALUES (1, 1000000, 0, 0), (2, 1000000, 0, 0)
                ON CONFLICT (id) DO UPDATE
                    SET balance = EXCLUDED.balance,
                        daily_sent_amount = 0,
                        version = 0
                """);

            s.execute("""
                INSERT INTO fee_revenue (id, total_collected, version)
                VALUES (1, 0, 0)
                ON CONFLICT (id) DO UPDATE
                    SET total_collected = 0,
                        version = 0
                """);

            s.execute("TRUNCATE TABLE transfer_log RESTART IDENTITY");
        }
    }
}
