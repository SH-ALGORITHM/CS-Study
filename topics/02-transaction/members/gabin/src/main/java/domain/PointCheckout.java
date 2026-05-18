package domain;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PointCheckout {

    private final long itemId;
    private final long userId;
    private final long orderId;
    private final int price;

    public PointCheckout(long orderId, long itemId, long userId, int price) {
        this.orderId = orderId;
        this.itemId = itemId;
        this.userId = userId;
        this.price = price;
    }
    /*
    *  고객 101, 102가 같은 상품을 동시에 포인트 결제
  각 트랜잭션은:
  1. 재고 조회
  2. 고객 포인트 조회
  3. 재고 차감
  4. 포인트 차감
  5. 주문 생성

  결과로 보여줄 수 있는 포인트:

  주문은 2건 생성됨
  포인트는 두 고객 각각 정상 차감됨
  그런데 공유 재고는 1번만 차감됨
  => checkout 정합성 문제로 Lost Update 확인
    * */
    public boolean checkout(Connection conn) throws Exception {
        int quantity = selectInt(conn, "SELECT quantity FROM stock WHERE item_id = ?", itemId);
        int balance = selectInt(conn, "SELECT balance FROM user_point WHERE user_id = ?", userId);

        if (quantity < 1 || balance < price) {
            return false;
        }

        Thread.sleep(100);

        int newQuantity = quantity - 1;
        int newBalance = balance - price;

        updateInt(conn, "UPDATE stock SET quantity = ? WHERE item_id = ?", newQuantity, itemId);
        updateInt(conn, "UPDATE user_point SET balance = ? WHERE user_id = ?", newBalance, userId);
        insertOrder(conn);

        return true;
    }

    public CurrentState currentState(Connection conn) throws SQLException {
        int quantity = selectInt(conn, "SELECT quantity FROM stock WHERE item_id = ?", itemId);
        int balance = selectInt(conn, "SELECT balance FROM user_point WHERE user_id = ?", userId);
        return new CurrentState(quantity, balance);
    }

    private static int selectInt(Connection conn, String sql, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("row not found");
                }
                return rs.getInt(1);
            }
        }
    }

    private static void updateInt(Connection conn, String sql, int value, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, value);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private void insertOrder(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO orders (order_id, user_id, item_id, paid_point, status)
            VALUES (?, ?, ?, ?, 'PAID')
            """)) {
            ps.setLong(1, orderId);
            ps.setLong(2, userId);
            ps.setLong(3, itemId);
            ps.setInt(4, price);
            ps.executeUpdate();
        }
    }

    public record CurrentState(int quantity, int balance) {
    }
}
