package infra;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 주식 매수/매도 도메인의 DB 스키마와 초기 데이터를 준비하는 유틸리티.
 *
 * STAGE 2/3 측정은 매번 같은 초기 상태에서 시작해야 결과를 비교할 수 있다.
 * 이 클래스는 wallet, holding 테이블을 생성하고,
 * user_id=1 사용자의 현금과 보유 주식을 초기값으로 되돌린다.
 *
 * version 컬럼은 낙관적 락에서 충돌 감지용으로 사용하므로
 * 초기화 시 항상 0으로 리셋한다.
 */
public final class SchemaBootstrap {

    /** 실습 대상 사용자 ID */
    public static final long USER_ID = 1L;

    /** 실습 대상 종목 코드. 005930은 삼성전자 종목 코드. */
    public static final String TICKER = "005930";

    private SchemaBootstrap() {
    }

    /**
     * wallet, holding 테이블을 만들고 실습 데이터를 초기 상태로 되돌린다.
     * 측정 전마다 호출해 이전 실행 결과가 다음 실험에 섞이지 않게 한다.
     */
    public static void resetStockTrade(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS wallet (
                    user_id BIGINT PRIMARY KEY,
                    cash NUMERIC NOT NULL,
                    version BIGINT NOT NULL DEFAULT 0
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS holding (
                    user_id BIGINT NOT NULL,
                    ticker VARCHAR(20) NOT NULL,
                    qty BIGINT NOT NULL,
                    version BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (user_id, ticker)
                )
                """);

            statement.execute("""
                INSERT INTO wallet (user_id, cash, version)
                VALUES (1, 1000000, 0)
                ON CONFLICT (user_id)
                DO UPDATE SET cash = EXCLUDED.cash, version = 0
                """);

            statement.execute("""
                INSERT INTO holding (user_id, ticker, qty, version)
                VALUES (1, '005930', 10, 0)
                ON CONFLICT (user_id, ticker)
                DO UPDATE SET qty = EXCLUDED.qty, version = 0
                """);
        }
    }
}
