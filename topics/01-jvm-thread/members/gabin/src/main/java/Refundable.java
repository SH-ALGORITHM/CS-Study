// 4개 환불 변종(NoSync / Sync / Atomic / Volatile)이 공통으로 구현하는 계약.
// runner에서 변종을 동일한 타입으로 다루기 위해 도입.
public interface Refundable {
    boolean refund() throws InterruptedException;
}
