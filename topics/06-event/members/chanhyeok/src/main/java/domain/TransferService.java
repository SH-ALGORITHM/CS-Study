package domain;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 6 주차 변환 — 5 주차 TransferService 에서 @Audited 제거 + @Transactional 적용 + publishEvent 추가.
 *
 * <h3>5 주차 → 6 주차 변경점</h3>
 * <ul>
 *   <li>@Audited 제거 — audit 책임이 TransferEventListeners 의 AFTER_COMMIT 으로 이동</li>
 *   <li>conn 직접 관리 (setAutoCommit / commit / rollback) → @Transactional 로 Spring 위임</li>
 *   <li>DataSourceUtils.getConnection — Spring 트랜잭션의 conn 받기 (같은 트랜잭션 유지)</li>
 *   <li>publishEvent(TransferCompletedEvent) — 메서드 끝에서 발행</li>
 * </ul>
 *
 * <h3>유지된 부분</h3>
 * <ul>
 *   <li>@DistributedLock — 5 주차 그대로. 트랜잭션 바깥에서 락 잡음</li>
 *   <li>P2PWallet.transferRaw — 송금 로직 그대로</li>
 * </ul>
 */
@Service
public class TransferService {

    private final DataSource dataSource;
    private final ApplicationEventPublisher publisher;
    private final P2PWallet wallet = new P2PWallet();

    public TransferService(DataSource dataSource, ApplicationEventPublisher publisher) {
        this.dataSource = dataSource;
        this.publisher = publisher;
    }

    @DistributedLock(key = "wallet:#{#fromId}", ttlSeconds = 5)
    @Transactional
    public void transfer(long fromId, long toId, BigDecimal amount) {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            wallet.transferRaw(conn, fromId, toId, amount);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }

        // commit 후 처리는 TransferEventListeners 의 AFTER_COMMIT 으로
        publisher.publishEvent(new TransferCompletedEvent(fromId, toId, amount, Instant.now()));
    }

    /**
     * STAGE 2 한계 재현용 — 송금 + publishEvent 후 일부러 예외 → rollback.
     *
     * <h3>관찰</h3>
     * <ul>
     *   <li>INSERT / UPDATE 는 rollback 으로 취소됨 (DB 정상)</li>
     *   <li>listener 는 publishEvent 시점에 이미 동기로 호출됨 (commit 전)</li>
     *   <li>= 5 주차 @Audited 한계와 동일 — 외부 알림 회수 불가</li>
     * </ul>
     */
    @DistributedLock(key = "wallet:#{#fromId}", ttlSeconds = 5)
    @Transactional
    public void transferWithFailure(long fromId, long toId, BigDecimal amount) {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            wallet.transferRaw(conn, fromId, toId, amount);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }

        publisher.publishEvent(new TransferCompletedEvent(fromId, toId, amount, Instant.now()));

        // ★ 일부러 예외 — rollback 시뮬레이션. publishEvent 후라 listener 는 이미 실행됨
        throw new RuntimeException("의도적 실패 (rollback 학습용)");
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
