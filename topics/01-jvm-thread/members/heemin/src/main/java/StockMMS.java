public class StockMMS {
    private int stock;

    public StockMMS(int stock) {
        this.stock = stock;
    }

    /*
    purchase(amount)
    1. stock이 amount 이상인지 확인
    2. 충분하면 stock에서 amount 차감
    3. 성공이면 true 반환
    4. 부족하면 false 반환
     */

    public boolean purchase(int amount) {
        if (stock >= amount) {
            // race condition 잘 보이게
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stock -= amount;
            return true;
        } else {
            return false;
        }
    }

    public int getStock() {
        return stock;
    }
}
