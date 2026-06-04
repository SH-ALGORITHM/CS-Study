package domain;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository repo;

    public OrderService(OrderRepository repo) {
        this.repo = repo;
    }

    @MyTransactional
    @Audited(action = "TRANSFER")
    public void transfer(long fromId, long toId, BigDecimal amount, boolean failMidway) {
        repo.minusBalance(fromId, amount);
        if (failMidway) {
            throw new RuntimeException("일부러 실패 — to 갱신 전에");
        }
        repo.plusBalance(toId, amount);
    }

    public BigDecimal getBalance(long id) {
        return repo.getBalance(id);
    }

    @MyTransactional
    @Audited(action = "OUTER")
    public void outerMethod(long id) {
        System.out.println("[OrderService] outerMethod 시작 — this 는 원본 (프록시 아님):");
        System.out.println("  this.getClass() = " + this.getClass().getName());
        innerMethod(id);
    }

    @MyTransactional
    @Audited(action = "INNER")
    public void innerMethod(long id) {
        System.out.println("[OrderService] innerMethod 호출됨 — but 프록시 안 거치면 [TX] 출력 X");
    }
}
