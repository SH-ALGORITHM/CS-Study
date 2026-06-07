package domain;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;

/** 비즈니스 로직을 담당하는 Service */
@Service
public class OrderService {

    private final OrderRepository repo;

    public OrderService(OrderRepository repo) {
        this.repo = repo;
    }

    /**
     * 계좌 이체 — STAGE 2-1 ThreadLocal 검증용.
     * Step 2 (Naive) 에서 일부러 예외 발생시켜 from 차감이 그대로 남는지 확인.
     */
    @MyTransactional
    @Audited(action = "TRANSFER")
    public void transfer(long fromId, long toId, BigDecimal amount, boolean failMidway) {
        repo.minusBalance(fromId, amount);
        if (failMidway) {
            throw new RuntimeException("일부러 실패 — to 갱신 전에");
        }
        repo.plusBalance(toId, amount);
    }

    /** 잔액 조회 — 트랜잭션 없이 호출. */
    public BigDecimal getBalance(long id) {
        return repo.getBalance(id);
    }

    /**
     * STAGE 4 self-invocation 함정 재현용 — outerMethod 가 this.innerMethod() 호출.
     *
     * <p>this.getClass() 출력은 원본 OrderService — Spring CGLIB 프록시가
     * 원본 인스턴스에 위임하는 구조이기 때문 (서브클래스 super 호출 X).
     */
    @MyTransactional
    @Audited(action = "OUTER")
    public void outerMethod(long id) {
        System.out.println("[OrderService] outerMethod 시작 — this 는 원본 (프록시 아님):");
        System.out.println("  this.getClass() = " + this.getClass().getName());
        innerMethod(id);
    }

    /**
     * this.innerMethod() 로 호출되면 프록시 우회 → @MyTransactional 무시됨.
     */
    @MyTransactional
    @Audited(action = "INNER")
    public void innerMethod(long id) {
        System.out.println("[OrderService] innerMethod 호출됨 — but 프록시 안 거치면 [TX] 출력 X");
    }
}
