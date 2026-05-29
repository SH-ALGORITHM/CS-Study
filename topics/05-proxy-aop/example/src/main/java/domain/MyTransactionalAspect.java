package domain;

import javax.sql.DataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * STAGE 2-1 Step 3 — ThreadLocal 로 Connection 공유하는 올바른 버전.
 *
 * <p>Spring 의 {@code TransactionSynchronizationManager} 본질:
 * Aspect 가 시작한 Connection 을 ThreadLocal 에 보관 → Repository 가 같은 conn 으로 받음.
 *
 * <p>{@link #currentConnection(DataSource)} 가 Spring 의 {@code DataSourceUtils.getConnection()} 에 해당.
 *
 * <p>{@code @Order(1)} = AOP 체이닝 시 가장 바깥. STAGE 2-2 양파 껍질 참고.
 */
@Aspect
@Order(1)
public class MyTransactionalAspect {

    private static final ThreadLocal<Connection> TX_CONN = new ThreadLocal<>();

    private final DataSource dataSource;

    public MyTransactionalAspect(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Around("@annotation(domain.MyTransactional)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        TX_CONN.set(conn);
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
            // ThreadLocal.remove() — 스레드풀 환경에서 다음 요청에 오염되지 않도록 필수
            TX_CONN.remove();
            // setAutoCommit / close 각각 try-catch — 한쪽 실패해도 다른쪽 실행 보장.
            // HikariCP 는 close 시 풀 설정으로 autoCommit 복원하므로 setAutoCommit(true) 는 사실상 중복.
            // 학습 코드라 명시.
            try { conn.setAutoCommit(true); } catch (java.sql.SQLException ignore) {}
            try { conn.close(); } catch (java.sql.SQLException ignore) {}
        }
    }

    /**
     * Repository 가 호출해서 현재 트랜잭션의 conn 을 받는다.
     * 트랜잭션 밖이면 새 conn (autoCommit=true) 반환.
     */
    public static Connection currentConnection(DataSource ds) throws SQLException {
        Connection conn = TX_CONN.get();
        return (conn != null) ? conn : ds.getConnection();
    }

    /** Repository 가 트랜잭션 안에서 받은 conn 인지 확인용. */
    public static boolean isTransactionActive() {
        return TX_CONN.get() != null;
    }
}
