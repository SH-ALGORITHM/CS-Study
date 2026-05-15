package domain;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class StudyRoomBooking {

    public boolean bookIfEmpty(Connection conn, int roomId, String start, String end, String userId) throws SQLException {
        // Phantom Read 를 유도하기 위해 SELECT 와 INSERT 를 분리

        String checkSql = """
            SELECT 1 FROM meeting_room_booking
            WHERE room_id = ?
              AND (start_at, end_at) OVERLAPS (?::TIMESTAMPTZ, ?::TIMESTAMPTZ)
            """;

        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setInt(1, roomId);
            ps.setString(2, start);
            ps.setString(3, end);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return false; // 이미 예약됨
                }
            }
        }

        // 비어있으면 INSERT
        String insertSql = """
            INSERT INTO meeting_room_booking (room_id, start_at, end_at, reserved_by)
            VALUES (?, ?::TIMESTAMPTZ, ?::TIMESTAMPTZ, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setInt(1, roomId);
            ps.setString(2, start);
            ps.setString(3, end);
            ps.setString(4, userId);
            ps.executeUpdate();
            return true;
        }
    }

    public long countBookings(Connection conn, int roomId, String start, String end) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM meeting_room_booking
            WHERE room_id = ?
              AND (start_at, end_at) OVERLAPS (?::TIMESTAMPTZ, ?::TIMESTAMPTZ)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ps.setString(2, start);
            ps.setString(3, end);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0;
    }
}
