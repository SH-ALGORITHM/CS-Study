public class Attendance {
    // 출근 도장
    private boolean checkedIn = false;

    // 출근 도장 찍는 로직
    public boolean checkIn() {

        if (!checkedIn) {
            checkedIn = true;
            return true;
        }

        return false;
    }
}
