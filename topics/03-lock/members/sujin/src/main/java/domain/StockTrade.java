package domain;

import infra.RedisClientFactory;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * 주식 매수/매도 도메인의 동시성 제어 실습 코드.
 *
 * wallet(현금)과 holding(보유 수량)을 read-modify-write 형태로 갱신한다.
 * 비관적 락은 항상 wallet -> holding 순서로 row lock을 잡아 데드락을 피하고,
 * 낙관적 락은 version 컬럼으로 충돌을 감지한다.
 */
public final class StockTrade {

    private static final int LOCK_TTL_SECONDS = 5;
    private static final String UNLOCK_SCRIPT = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
          return redis.call('del', KEYS[1])
        else
          return 0
        end
        """;

    private final DataSource dataSource;

    public StockTrade(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 비관적 락으로 주식 매수를 처리한다.
     *
     * 항상 wallet -> holding 순서로 FOR UPDATE를 걸어 데드락의 순환 대기를 피한다.
     * 현금이 부족하면 rollback 후 false를 반환한다.
     */
    public boolean buyPessimistic(long userId, String ticker, long qty, BigDecimal price) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Wallet wallet = lockWallet(connection, userId);
                Holding holding = lockHolding(connection, userId, ticker);

                BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(qty));
                if (wallet.cash().compareTo(totalPrice) < 0) {
                    connection.rollback();
                    return false;
                }

                updateWallet(connection, userId, wallet.cash().subtract(totalPrice));
                updateHolding(connection, userId, ticker, holding.qty() + qty);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * 비관적 락으로 주식 매도를 처리한다.
     *
     * 매도 역시 holding을 먼저 잠그지 않고 wallet -> holding 순서를 지킨다.
     * 보유 수량이 부족하면 rollback 후 false를 반환한다.
     */
    public boolean sellPessimistic(long userId, String ticker, long qty, BigDecimal price) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Wallet wallet = lockWallet(connection, userId);
                Holding holding = lockHolding(connection, userId, ticker);

                if (holding.qty() < qty) {
                    connection.rollback();
                    return false;
                }

                BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(qty));
                updateWallet(connection, userId, wallet.cash().add(totalPrice));
                updateHolding(connection, userId, ticker, holding.qty() - qty);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * 낙관적 락으로 주식 매수를 처리한다.
     *
     * FOR UPDATE 없이 cash/qty와 version을 읽고,
     * UPDATE ... WHERE version = ? 결과가 0이면 충돌로 판단해 재시도한다.
     * maxRetries 안에 성공하지 못하면 false를 반환한다.
     */
    public boolean buyOptimistic(long userId, String ticker, long qty, BigDecimal price, int maxRetries)
        throws SQLException {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Wallet wallet = readWallet(connection, userId);
                    Holding holding = readHolding(connection, userId, ticker);

                    BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(qty));
                    if (wallet.cash().compareTo(totalPrice) < 0) {
                        connection.rollback();
                        return false;
                    }

                    boolean updated = updateWalletVersioned(
                        connection,
                        userId,
                        wallet.cash().subtract(totalPrice),
                        wallet.version()
                    ) && updateHoldingVersioned(
                        connection,
                        userId,
                        ticker,
                        holding.qty() + qty,
                        holding.version()
                    );

                    if (updated) {
                        connection.commit();
                        return true;
                    }

                    connection.rollback();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        }
        return false;
    }

    /**
     * 낙관적 락으로 주식 매도를 처리한다.
     *
     * wallet 또는 holding 중 하나라도 version update에 실패하면
     * 전체 트랜잭션을 rollback하고 처음부터 재시도한다.
     */
    public boolean sellOptimistic(long userId, String ticker, long qty, BigDecimal price, int maxRetries)
        throws SQLException {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Wallet wallet = readWallet(connection, userId);
                    Holding holding = readHolding(connection, userId, ticker);

                    if (holding.qty() < qty) {
                        connection.rollback();
                        return false;
                    }

                    BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(qty));
                    boolean updated = updateWalletVersioned(
                        connection,
                        userId,
                        wallet.cash().add(totalPrice),
                        wallet.version()
                    ) && updateHoldingVersioned(
                        connection,
                        userId,
                        ticker,
                        holding.qty() - qty,
                        holding.version()
                    );

                    if (updated) {
                        connection.commit();
                        return true;
                    }

                    connection.rollback();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        }
        return false;
    }


    /**
     * Redis 종목 단위 분산락을 잡은 뒤 주식 매수를 처리한다.
     *
     * lock:ticker:{ticker} 키로 외부 거래소 API 같은 DB 밖 자원을 직렬화하는 흐름을 흉내낸다.
     * DB row 정합성은 내부에서 비관적 락 메서드를 재사용해 지킨다.
     */
    public boolean buyWithDistributedLock(long userId, String ticker, long qty, BigDecimal price) throws SQLException {
        return withTickerLock(ticker, () -> buyPessimistic(userId, ticker, qty, price));
    }


    /**
     * Redis 종목 단위 분산락을 잡은 뒤 주식 매도를 처리한다.
     *
     * 같은 종목에 대한 여러 인스턴스의 동시 진입을 막고,
     * 작업 후 Lua script로 본인이 잡은 lock만 해제한다.
     */
    public boolean sellWithDistributedLock(long userId, String ticker, long qty, BigDecimal price) throws SQLException {
        return withTickerLock(ticker, () -> sellPessimistic(userId, ticker, qty, price));
    }


    /**
     * ticker 단위 Redis lock을 획득한 동안 action을 실행한다.
     *
     * SET key value NX EX로 TTL이 있는 lock을 잡고,
     * finally에서 Lua script로 get + del을 원자적으로 수행한다.
     * lock 획득에 실패하면 action을 실행하지 않고 false를 반환한다.
     */
    private boolean withTickerLock(String ticker, SqlBooleanSupplier action) throws SQLException {
        String lockKey = "lock:ticker:" + ticker;
        String lockValue = UUID.randomUUID().toString();

        try (StatefulRedisConnection<String, String> connection = RedisClientFactory.connect()) {
            RedisCommands<String, String> redis = connection.sync();
            String result = redis.set(lockKey, lockValue, SetArgs.Builder.nx().ex(LOCK_TTL_SECONDS));
            if (!"OK".equals(result)) {
                return false;
            }

            try {
                return action.getAsBoolean();
            } finally {
                redis.eval(UNLOCK_SCRIPT, ScriptOutputType.INTEGER, new String[]{lockKey}, lockValue);
            }
        }
    }

    private Wallet lockWallet(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT cash, version
            FROM wallet
            WHERE user_id = ?
            FOR UPDATE
            """)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("wallet not found: userId=" + userId);
                }
                return new Wallet(resultSet.getBigDecimal("cash"), resultSet.getLong("version"));
            }
        }
    }

    private Holding lockHolding(Connection connection, long userId, String ticker) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT qty, version
            FROM holding
            WHERE user_id = ? AND ticker = ?
            FOR UPDATE
            """)) {
            statement.setLong(1, userId);
            statement.setString(2, ticker);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("holding not found: userId=" + userId + ", ticker=" + ticker);
                }
                return new Holding(resultSet.getLong("qty"), resultSet.getLong("version"));
            }
        }
    }

    private Wallet readWallet(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT cash, version
            FROM wallet
            WHERE user_id = ?
            """)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("wallet not found: userId=" + userId);
                }
                return new Wallet(resultSet.getBigDecimal("cash"), resultSet.getLong("version"));
            }
        }
    }

    private Holding readHolding(Connection connection, long userId, String ticker) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT qty, version
            FROM holding
            WHERE user_id = ? AND ticker = ?
            """)) {
            statement.setLong(1, userId);
            statement.setString(2, ticker);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("holding not found: userId=" + userId + ", ticker=" + ticker);
                }
                return new Holding(resultSet.getLong("qty"), resultSet.getLong("version"));
            }
        }
    }

    private void updateWallet(Connection connection, long userId, BigDecimal nextCash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE wallet
            SET cash = ?
            WHERE user_id = ?
            """)) {
            statement.setBigDecimal(1, nextCash);
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }

    private void updateHolding(Connection connection, long userId, String ticker, long nextQty) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE holding
            SET qty = ?
            WHERE user_id = ? AND ticker = ?
            """)) {
            statement.setLong(1, nextQty);
            statement.setLong(2, userId);
            statement.setString(3, ticker);
            statement.executeUpdate();
        }
    }

    private boolean updateWalletVersioned(Connection connection, long userId, BigDecimal nextCash, long version)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE wallet
            SET cash = ?, version = version + 1
            WHERE user_id = ? AND version = ?
            """)) {
            statement.setBigDecimal(1, nextCash);
            statement.setLong(2, userId);
            statement.setLong(3, version);
            return statement.executeUpdate() == 1;
        }
    }

    private boolean updateHoldingVersioned(Connection connection, long userId, String ticker, long nextQty, long version)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE holding
            SET qty = ?, version = version + 1
            WHERE user_id = ? AND ticker = ? AND version = ?
            """)) {
            statement.setLong(1, nextQty);
            statement.setLong(2, userId);
            statement.setString(3, ticker);
            statement.setLong(4, version);
            return statement.executeUpdate() == 1;
        }
    }

    private record Wallet(BigDecimal cash, long version) {
    }

    private record Holding(long qty, long version) {
    }

    @FunctionalInterface
    private interface SqlBooleanSupplier {
        boolean getAsBoolean() throws SQLException;
    }
}
