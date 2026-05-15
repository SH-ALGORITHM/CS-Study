package infra;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public final class SchemaBootstrap {

    private SchemaBootstrap() {}

    public static void resetBookingTable(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS meeting_room_booking (
                    id SERIAL PRIMARY KEY,
                    room_id INT NOT NULL,
                    start_at TIMESTAMPTZ NOT NULL,
                    end_at TIMESTAMPTZ NOT NULL,
                    reserved_by TEXT NOT NULL
                )
                """);
            s.execute("TRUNCATE meeting_room_booking");
        }
    }

    public static void initBooking(DataSource ds, int roomId, String start, String end, String reservedBy) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO meeting_room_booking (room_id, start_at, end_at, reserved_by) VALUES (?, ?::TIMESTAMPTZ, ?::TIMESTAMPTZ, ?)")) {
            ps.setInt(1, roomId);
            ps.setString(2, start);
            ps.setString(3, end);
            ps.setString(4, reservedBy);
            ps.executeUpdate();
        }
    }
}
