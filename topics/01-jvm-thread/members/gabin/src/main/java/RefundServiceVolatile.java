//volatile -> 원자성 꺠지는 것 확인
public class RefundServiceVolatile implements Refundable {
    private volatile boolean refunded = false; //환불 체크 여부 변수
    private int refundCount = 0; //실제 내부 환불 처리 횟수 -> s1에서의 성공카운트는 api 호출 횟수.
    private int refundedAmount = 0; //총 환불 금액
    private final int paymentAmount = 10000; //지불 금액

    //환불 메서드 : 환불이 이루어지면 t/ 아니면 f 반환
    public boolean refund() throws InterruptedException {
        if(!refunded) {
            Thread.sleep(10);

            refundCount++;
            refundedAmount += paymentAmount;

            refunded = true;
            return true;
        }
        return false;
    }

    //환불 여부 체크 메서드
    public boolean isRefunded() {
        return refunded;
    }

    public int getRefundCount() {
        return refundCount;
    }

    public int getRefundedAmount() {
        return refundedAmount;
    }

    public int getPaymentAmount() {
        return paymentAmount;
    }
}
