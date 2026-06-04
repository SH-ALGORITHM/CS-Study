package domain;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

/**
 * 5 주차 핵심 — @DistributedLock 한 줄로 3 주차 보일러플레이트 추출 결과.
 *
 * <p>3 주차 {@link stage.Stage1Before} 의 lockKey/lockValue/SETNX/Lua/finally 가 모두 사라짐.
 * 비즈니스 로직 (transferRaw 호출) 만 남음.
 *
 * <p>SpEL {@code #fromId} 는 메서드 인자 {@code fromId} 직접 참조.
 * id 순서 정규화 (작은 쪽 먼저) 는 호출자가 책임 — 3 주차 데드락 회피 패턴 그대로.
 */
@Service
public class TransferService {

    private final DataSource dataSource;
    private final P2PWallet wallet = new P2PWallet();

    public TransferService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @DistributedLock(key = "wallet:#{fromId}", ttlSeconds = 5)
    public void transfer(long fromId, long toId, BigDecimal amount) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                wallet.transferRaw(conn, fromId, toId, amount);
                conn.commit();
            } catch (Throwable t) {
                conn.rollback();
                throw t;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public BigDecimal balanceOf(long id) {
        try (Connection conn = dataSource.getConnection()) {
            return wallet.balanceOf(conn, id);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public BigDecimal feeTotal() {
        try (Connection conn = dataSource.getConnection()) {
            return wallet.feeTotal(conn);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
