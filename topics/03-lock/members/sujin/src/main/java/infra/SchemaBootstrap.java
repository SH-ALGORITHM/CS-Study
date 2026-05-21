package infra;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 주식 매수/매도 도메인의 DB 스키마와 초기 데이터를 준비하는 유틸리티.
 *
 * STAGE 2/3 측정은 매번 같은 초기 상태에서 시작해야 결과를 비교할 수 있다.
 * 이 클래스는 wallet, holding 테이블을 생성하고
 * 실습 대상 사용자의 현금과 보유 주식을 초기값으로 되돌린다.
 *
 * version 컬럼은 낙관적 락에서 충돌 감지용으로 사용하므로
 * 초기화 시 항상 0으로 리셋한다.
 */
public final class SchemaBootstrap {

    /** 실습 대상 사용자 ID */
    public static final long USER_ID = 1L;

    /** 실습 대상 종목 코드. 005930은 삼성전자 종목 코드. */
    public static final String TICKER = "005930";

    /** 모든 실험이 시작할 때 사용할 기본 현금. */
    public static final String INITIAL_CASH = "1000000";

    /** 모든 실험이 시작할 때 사용할 기본 보유 수량. */
    public static final long INITIAL_QTY = 10L;

    private SchemaBootstrap() {
    }

    /**
     * wallet, holding 테이블을 만들고 실습 데이터를 초기 상태로 되돌린다.
     * 측정 전마다 호출해 이전 실행 결과가 다음 실험에 섞이지 않게 한다.
     */
    public static void resetStockTrade(DataSource dataSource) throws SQLException {
        resetStockTrade(dataSource, 1);
    }

    /**
     * STAGE 3 측정을 위해 여러 사용자/종목 조합을 같은 초기값으로 준비한다.
     *
     * portfolioCount가 1이면 모든 요청이 같은 row에 몰리고,
     * 10 또는 100이면 요청이 여러 row로 분산되어 충돌 빈도가 낮아진다.
     */
    public static void resetStockTrade(DataSource dataSource, int portfolioCount) throws SQLException {
        if (portfolioCount <= 0) {
            throw new IllegalArgumentException("portfolioCount must be positive");
        }

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

            statement.execute("TRUNCATE holding, wallet");

            try (PreparedStatement wallet = connection.prepareStatement("""
                INSERT INTO wallet (user_id, cash, version)
                VALUES (?, ?, 0)
                """);
                 PreparedStatement holding = connection.prepareStatement("""
                INSERT INTO holding (user_id, ticker, qty, version)
                VALUES (?, ?, ?, 0)
                """)) {
                for (int index = 0; index < portfolioCount; index++) {
                    long userId = USER_ID + index;
                    String ticker = tickerOf(index);

                    wallet.setLong(1, userId);
                    wallet.setBigDecimal(2, new java.math.BigDecimal(INITIAL_CASH));
                    wallet.addBatch();

                    holding.setLong(1, userId);
                    holding.setString(2, ticker);
                    holding.setLong(3, INITIAL_QTY);
                    holding.addBatch();
                }

                wallet.executeBatch();
                holding.executeBatch();
            }
        }
    }

    /**
     * STAGE 3에서 row 분산 정도를 만들기 위한 종목 코드.
     * 첫 번째 row는 기존 STAGE 2와 같은 005930을 사용한다.
     */
    public static String tickerOf(int index) {
        if (index == 0) {
            return TICKER;
        }
        return "S%05d".formatted(index);
    }
}
