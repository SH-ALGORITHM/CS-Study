package infra;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 호텔 객실 예약 도메인의 측정 전 상태 리셋 유틸.
 *
 * <h3>왜 매번 리셋하나</h3>
 * 1주차 측정 원칙과 동일 — 이전 측정의 잔여 상태가 다음 측정에 영향을 주면
 * 결과 해석이 어려워진다. {@code TRUNCATE} + 시드로 명시적 초기화.
 *
 * <h3>idempotent 보장</h3>
 * {@code CREATE TABLE IF NOT EXISTS} + {@code ON CONFLICT DO NOTHING} →
 * 처음 실행 시 테이블 + 시드 생성, 재실행 시 무해.
 *
 * <h3>STAGE 별 사용법</h3>
 * <ul>
 *   <li>STAGE 2: 매 측정 라운드 전 {@link #resetRoomBookings(DataSource)}</li>
 *   <li>STAGE 3: 격리 수준별 측정 사이마다 {@link #resetRoomBookings(DataSource)},
 *       EXCLUDE 비교 측정 시 {@link #addExcludeConstraint(DataSource)} /
 *       {@link #dropExcludeConstraint(DataSource)} 로 조건 균등화</li>
 * </ul>
 */
public final class SchemaBootstrap {

    private SchemaBootstrap() {}

    /**
     * 측정 시작 / 사이 호출 — 호텔 도메인을 깨끗한 상태로 복원.
     *
     * <h3>동작</h3>
     * <ol>
     *   <li>{@code room}, {@code room_booking} 테이블 존재 보장 (없으면 생성)</li>
     *   <li>{@code room} 마스터 데이터 시드 (101 standard, 102 standard, 103 deluxe)
     *       — 이미 있으면 {@code ON CONFLICT DO NOTHING} 으로 무시</li>
     *   <li>{@code room_booking} 만 {@code TRUNCATE} — room 마스터는 유지</li>
     * </ol>
     *
     * <p>{@code DROP TABLE} 안 함 — 매 측정마다 DROP/CREATE 하면 인덱스 재생성 비용까지 들어
     * 측정 노이즈가 됨. 마스터 보존 + 트랜잭션 row 만 정리하는 가벼운 리셋.
     */
    public static void resetRoomBookings(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            // [1] 테이블 존재 보장
            s.execute("""
                CREATE TABLE IF NOT EXISTS room (
                    room_no INTEGER PRIMARY KEY,
                    room_type VARCHAR(20) NOT NULL DEFAULT 'standard'
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS room_booking (
                    id BIGSERIAL PRIMARY KEY,
                    room_no INTEGER NOT NULL REFERENCES room(room_no),
                    check_in DATE NOT NULL,
                    check_out DATE NOT NULL,
                    guest_name VARCHAR(100) NOT NULL
                )
                """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_room_booking_room ON room_booking (room_no)");

            // [2] room 마스터 시드 (없으면 INSERT, 있으면 무시)
            s.execute("""
                INSERT INTO room (room_no, room_type) VALUES
                    (101, 'standard'),
                    (102, 'standard'),
                    (103, 'deluxe')
                ON CONFLICT (room_no) DO NOTHING
                """);

            // [3] room_booking 만 비우기 — room 마스터는 유지
            s.execute("TRUNCATE room_booking RESTART IDENTITY");
        }
    }

    /**
     * STAGE 3 — EXCLUDE constraint 비교 측정용. 제약 추가.
     *
     * <h3>왜 필요한가</h3>
     * STAGE 3 의 측정 비교는 격리 수준 4 단계 + EXCLUDE 까지 5 케이스.
     * EXCLUDE 만 따로 측정하려면 측정 시작 전 제약 추가, 측정 끝나면 제거
     * (다른 격리 수준 측정 시 조건 동일하게 유지).
     *
     * <h3>동작</h3>
     * <ol>
     *   <li>{@code btree_gist} 확장 보장 — {@code room_no WITH =} 비교를 위해 필요
     *       (GiST 가 기본은 범위 타입만 지원, btree_gist 확장이 = 연산 가능하게 해줌)</li>
     *   <li>기존 제약 있으면 제거 후 다시 추가 — idempotent</li>
     * </ol>
     *
     * <h3>의미</h3>
     * {@code (room_no, daterange(check_in, check_out))} 두 조건이 모두 true 면 INSERT 거부.
     * = 같은 객실 + 시간대 겹침 = 이중 예약 → DB 가 자동 차단.
     * 발생 시 {@code SQLState 23P01 (exclusion_violation)}.
     */
    public static void addExcludeConstraint(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE EXTENSION IF NOT EXISTS btree_gist");
            s.execute("ALTER TABLE room_booking DROP CONSTRAINT IF EXISTS no_overlap");
            s.execute("""
                ALTER TABLE room_booking
                ADD CONSTRAINT no_overlap
                EXCLUDE USING gist (
                    room_no WITH =,
                    daterange(check_in, check_out) WITH &&
                )
                """);
        }
    }

    /**
     * STAGE 3 — EXCLUDE 제약 제거. 다른 격리 수준 측정 전 호출.
     *
     * <p>EXCLUDE 가 걸린 상태에서 RC/RR/SR 측정하면 EXCLUDE 가 race 를 막아버려
     * 격리 수준의 효과를 따로 볼 수 없음. 측정 조건 균등화를 위해 매 케이스 시작 시 호출.
     */
    public static void dropExcludeConstraint(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("ALTER TABLE room_booking DROP CONSTRAINT IF EXISTS no_overlap");
        }
    }

    public static void disableAutovacuum(DataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE room_booking SET (autovacuum_enabled = false)");
        }
    }

    public static void enableAutovacuum(DataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE room_booking SET (autovacuum_enabled = true)");
        }
    }
}
