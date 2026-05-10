import java.util.concurrent.atomic.AtomicInteger;

public class StockMMS {
    private AtomicInteger stock;

    public StockMMS(int stock) {
        this.stock = new AtomicInteger(stock);
    }

    public boolean purchase(int amount) {
        while (true) {
            int current = stock.get();

            // 재고 부족
            if (current < amount) {
                return false;
            }

            // CAS: 현재값이 안 바뀌었으면 차감
            if (stock.compareAndSet(current, current - amount)) {
                return true;
            }

            // 실패하면 다른 스레드가 먼저 변경한 것 → 다시 시도
        }
    }

    public int getStock() {
        return stock.get();
    }

    /*
    // synchronized 버전
    private int stock;

    public StockMMS(int stock) {
        this.stock = stock;
    }

    public synchronized boolean purchase(int amount) {
        if (stock >= amount) {
            stock -= amount;
            return true;
        }
        return false;
    }

    public int getStock() {
        return stock;
    }
    */
}
