/**
 * 1주차 예시 도메인 — 은행 계좌.
 *
 * <h3>이 클래스가 하는 일</h3>
 * 잔고에서 출금하는 단순한 클래스. 기능은 한 가지: withdraw()로 잔고 차감.
 *
 * <h3>왜 race condition이 발생하는가</h3>
 * 여러 스레드가 동시에 withdraw()를 호출하면 다음 순서가 가능합니다.
 *
 * <pre>
 *   초기 잔고: 100원
 *
 *   시점 1) 스레드 A: withdraw(50) 호출 → if (balance >= 50) 통과 (100 >= 50)
 *   시점 2) 스레드 B: withdraw(50) 호출 → if (balance >= 50) 통과 (100 >= 50)
 *   시점 3) 스레드 A: balance -= 50 실행 → balance = 50
 *   시점 4) 스레드 B: balance -= 50 실행 → balance = 0
 *
 *   결과: 둘 다 출금 성공했지만 사실 100원밖에 없었음.
 *         (스레드가 더 많거나 작업 시간이 길면 잔고 음수까지 발생 가능)
 * </pre>
 *
 * <h3>왜 의도적으로 thread-safe 하지 않게 만들었나</h3>
 * STAGE 1에서 race를 직접 보기 위함. STAGE 3에서 synchronized / AtomicInteger
 * 등으로 해결하는 과정을 배웁니다.
 */
public class BankAccount {

    // 공유 자원 — 여러 스레드가 동시에 만지는 변수.
    // private 이지만 메서드를 통해 여러 스레드가 접근하므로 race가 발생함.
    private int balance;

    public BankAccount(int initialBalance) {
        this.balance = initialBalance;
    }

    /**
     * 출금 시도. 잔고가 amount 이상이면 차감 후 true 반환.
     *
     * <p>⚠️ 이 메서드는 thread-safe 하지 않습니다 (의도적으로).
     * 여러 스레드가 동시에 호출하면 race condition이 발생할 수 있습니다.
     *
     * @param amount 출금할 금액
     * @return 출금 성공 여부
     */
    public boolean withdraw(int amount) {
        // [읽기 단계] 현재 잔고가 충분한지 확인
        if (balance >= amount) {

            // ⚠️ 여기 사이가 race가 발생하는 "위험 구간"입니다.
            //    스레드 스케줄러는 이 시점에 다른 스레드로 컨텍스트 스위칭 가능.
            //    그러면 다른 스레드도 위 if 조건을 통과해버립니다.

            // [쓰기 단계] 잔고 차감
            balance -= amount;
            return true;
        }
        return false;
    }

    /**
     * 현재 잔고 조회 (확인용).
     */
    public int getBalance() {
        return balance;
    }
}
