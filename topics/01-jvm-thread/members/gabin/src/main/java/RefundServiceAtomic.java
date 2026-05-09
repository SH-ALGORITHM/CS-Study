import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

//Atomic 적용
public class RefundServiceAtomic implements Refundable {
    private final AtomicBoolean refunded = new AtomicBoolean(false); //환불 체크 여부 변수
    private final AtomicInteger refundCount = new AtomicInteger(0); //실제 내부 환불 처리 횟수 -> s1에서의 성공카운트는 api 호출 횟수.
    private int refundedAmount = 0; //총 환불 금액
    private final int paymentAmount = 10000; //지불 금액

    //환불 메서드 : 환불이 이루어지면 t/ 아니면 f 반환
    public boolean refund() throws InterruptedException {
        //현재 refunded가 false이면 true로 바꾸고 성공 처리
        //이미 true이면 실패 처리
        if(refunded.compareAndSet(false,true)) {
            Thread.sleep(10);

            refundCount.incrementAndGet();
            refundedAmount += paymentAmount;

            return true;
        }
        return false;
    }


    public int getRefundedAmount() {
        return refundedAmount;
    }

    public int getPaymentAmount() {
        return paymentAmount;
    }
}
