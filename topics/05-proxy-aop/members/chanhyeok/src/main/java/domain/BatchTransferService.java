package domain;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Stage3 — self-invocation 해결책 (c) 클래스 분리 시연.
 *
 * <p>{@link TransferService} 를 외부 주입으로 받아서 호출 → 매 호출마다 프록시 거침 → 락 정상.
 */
@Service
public class BatchTransferService {

    private final TransferService transferSvc;

    public BatchTransferService(TransferService transferSvc) {
        this.transferSvc = transferSvc;
    }

    public void batchTransfer(List<long[]> pairs, BigDecimal amount) {
        for (long[] pair : pairs) {
            // ★ 별도 빈 호출 — 매번 프록시 거침 → @DistributedLock 정상 작동
            transferSvc.transfer(pair[0], pair[1], amount);
        }
    }
}
