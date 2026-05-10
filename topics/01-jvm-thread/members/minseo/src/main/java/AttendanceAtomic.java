import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AttendanceAtomic implements Attendance{

    // 출근 도장
    private AtomicBoolean checkedIn = new AtomicBoolean(false);

    // 출근 도장 찍는 로직
    @Override
    public boolean checkIn() {
        // 내부의 boolean 값을 확인하고 바꾸는 걸 한 번에 처리
        return checkedIn.compareAndSet(false, true);
    }
}
