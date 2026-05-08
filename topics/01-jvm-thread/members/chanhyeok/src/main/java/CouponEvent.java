/**
 * 1주차 쿠폰 발급 도메인.
 *
 * <p>availableCoupons 필드에 대한 read-modify-write가 race condition을 일으킨다.
 * 이를 STAGE 1에서 직접 시연하고, STAGE 3에서 synchronized / AtomicInteger로 해결한다.
 */
public class CouponEvent {

    private int availableCoupons; // 공유자원 (쿠폰)

    public CouponEvent(int availableCoupons) {
        this.availableCoupons = availableCoupons; // 초기 쿠폰 수를 받는 생성자
    }

    public boolean claim(int amount) {
        if (availableCoupons >= amount) { // read: 쿠폰이 충분한지
            availableCoupons -= amount; // write: 쿠폰 차감
            return true; // 성공
        }
        return false; // 실패 (쿠폰 부족)
    }

    public int getAvailableCoupons() {
        return availableCoupons; // 잔여 쿠폰 조회
    }
}
