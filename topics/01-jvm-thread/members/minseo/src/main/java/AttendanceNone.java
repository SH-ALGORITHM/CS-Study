public class AttendanceNone implements Attendance{
    // 출근 도장
    private boolean checkedIn = false;

    // 출근 도장 찍는 로직
    @Override
    public boolean checkIn() {

        if (!checkedIn) {
            // 다른 스레드가 끼어들 틈을 줌
            try { Thread.sleep(1); } catch (Exception e) {}

            checkedIn = true;
            return true;
        }

        return false;
    }
}
