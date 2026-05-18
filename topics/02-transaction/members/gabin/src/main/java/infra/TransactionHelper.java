package infra;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class TransactionHelper {

    @FunctionalInterface
    public interface SqlAction<T> {
        T run(Connection conn) throws Exception;
    }

    private TransactionHelper() {
    }

    public static <T> T execute(DataSource ds, int isolationLevel, SqlAction<T> action) throws Exception {
        try (Connection conn = ds.getConnection()) {
            int previousIso = conn.getTransactionIsolation();
            boolean previousAutoCommit = conn.getAutoCommit();

            try {
                conn.setAutoCommit(false);
                conn.setTransactionIsolation(isolationLevel);

                T result = action.run(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                rollbackQuietly(conn);
                throw e;
            } finally {
                restoreQuietly(conn, previousIso, previousAutoCommit);
            }
        }
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static void restoreQuietly(Connection conn, int isolationLevel, boolean autoCommit) {
        try {
            conn.setTransactionIsolation(isolationLevel);
        } catch (SQLException ignored) {
        }
        try {
            conn.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
        }
    }
}
