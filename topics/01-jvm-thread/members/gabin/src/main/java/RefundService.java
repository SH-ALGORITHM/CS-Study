//환불 클래스
public class RefundService {
    private boolean refunded = false; //환불 체크 여부 변수
    private int refundCount = 0; //실제 내부 환불 처리 횟수 -> s1에서의 성공카운트는 api 호출 횟수.
    private int refundedAmount = 0; //총 환불 금액
    private final int paymentAmount = 10000; //지불 금액

    //환불 메서드 : 환불이 이루어지면 t/ 아니면 f 반환
    public boolean refund() throws InterruptedException {
        if(!refunded) {
            //조건 문 통과 후 다른 스레드가 끼어들 수 있도록 시간 확보 -> 진단용으로 시간 간격 벌려 놓은 거기 때문이기에 s2 해결책 측정 시에는 제거해야 함.
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
