package domain;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(2)          // 권한 안쪽 — 통과한 요청만 트랜잭션
public class MyTransactionalAspect {

    private final DataSource dataSource;
    // ★ 현재 스레드에 묶인 Connection 보관 — 이게 TransactionSynchronizationManager 의 본질
    private static final ThreadLocal<Connection> TX_CONN = new ThreadLocal<>();

    public MyTransactionalAspect(DataSource dataSource) { this.dataSource = dataSource; }

    @Around("@annotation(MyTransactional)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        TX_CONN.set(conn);                                   // ★ 스레드에 바인딩
        System.out.println("[TX] begin — " + pjp.getSignature().getName());
        try {
            Object result = pjp.proceed();
            conn.commit();
            System.out.println("[TX] commit");
            return result;
        } catch (Throwable t) {
            conn.rollback();
            System.out.println("[TX] rollback — " + t.getMessage());
            throw t;
        } finally {
            TX_CONN.remove();                                // ★ 누수 방지 (스레드풀 오염 방지)
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    /** Repository 가 같은 트랜잭션 커넥션을 받는 통로. 트랜잭션 밖이면 새 커넥션. */
    public static Connection currentConnection(DataSource ds) throws SQLException {
        Connection conn = TX_CONN.get();
        return (conn != null) ? conn : ds.getConnection();
    }

    /** 트랜잭션 커넥션이면 닫지 않음(Aspect 가 닫음). 밖에서 연 커넥션만 닫음. */
    public static void releaseConnection(Connection conn) throws SQLException {
        if (conn != TX_CONN.get()) {
            conn.close();
        }
    }
}
