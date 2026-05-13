package domain;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class RoomBooking {

    private static final String SELECT_SQL = """
        SELECT 1 FROM room_booking
        WHERE room_no = ?
            AND daterange(check_in, check_out) && daterange(?, ?)
        """;

    private static final String INSERT_SQL = """
        INSERT INTO room_booking (room_no, check_in, check_out, guest_name)
        VALUES (?, ?, ?, ?)
        """;

    private static final String COUNT_SQL = """
        SELECT COUNT(*) FROM room_booking
        WHERE room_no = ?
            AND daterange(check_in, check_out) && daterange(?, ?)
        """;


    public boolean tryBook(Connection conn, int roomNo, LocalDate checkIn,
                           LocalDate checkOut, String guestName) throws SQLException {
        boolean alreadyBooked;
        try (PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setInt(1, roomNo);
            ps.setObject(2, checkIn);
            ps.setObject(3, checkOut);

            try (ResultSet rs = ps.executeQuery()) {
                alreadyBooked = rs.next();
            }
        }

        if (alreadyBooked) {
            return false;
        }

        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)){
            ps.setInt(1, roomNo);
            ps.setObject(2, checkIn);
            ps.setObject(3, checkOut);
            ps.setString(4, guestName);
            ps.executeUpdate();
        }

        return true;
    }

    public int countBookings(Connection conn, int roomNo, LocalDate checkIn,
                             LocalDate checkOut) throws SQLException{
        try (PreparedStatement ps = conn.prepareStatement(COUNT_SQL)) {
            ps.setInt(1, roomNo);
            ps.setObject(2, checkIn);
            ps.setObject(3, checkOut);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
