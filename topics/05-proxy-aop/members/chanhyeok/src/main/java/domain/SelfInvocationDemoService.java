package domain;

import java.math.BigDecimal;
import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

/**
 * Stage3 — self-invocation 함정 본인 도메인 시연용.
 *
 * <h3>시나리오</h3>
 * <ul>
 *   <li>{@link #outerNoLock} 가 self-invocation 으로 {@link #innerTransfer} 호출</li>
 *   <li>innerTransfer 에 @DistributedLock 있지만 this. 호출이라 프록시 우회</li>
 *   <li>→ [LOCK] 출력 X. DB UPDATE 만 발생 (락 없이)</li>
 * </ul>
 *
 * <p>해결책: {@link domain.BatchTransferService} 처럼 별도 빈으로 분리 (5 주차 권장 (c) 클래스 분리).
 */
@Service
public class SelfInvocationDemoService {

    private final DataSource dataSource;
    private final P2PWallet wallet = new P2PWallet();

    public SelfInvocationDemoService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * @DistributedLock 없음. 내부에서 this.innerTransfer() 호출 → 프록시 우회.
     */
    public void outerNoLock(long from, long to, BigDecimal amount) {
        System.out.println("[outer] this.getClass() = " + this.getClass().getName());
        System.out.println("[outer] this.innerTransfer() 호출 — 프록시 거치지 않음");
        innerTransfer(from, to, amount);   // self-invocation — @DistributedLock 우회
    }

    @DistributedLock(key = "wallet:#{from}", ttlSeconds = 5)
    public void innerTransfer(long from, long to, BigDecimal amount) {
        System.out.println("[inner] 실행됨 — but [LOCK] 출력 없음 (advice 미적용)");
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            wallet.transferRaw(conn, from, to, amount);
            conn.commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
