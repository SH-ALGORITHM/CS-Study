public class Work extends Thread {

    // 퇴근 도장
    // volatile을 붙이면 캐시 쓰지 말고 메인 메모리에서 직접 보라는 뜻
    volatile boolean checkOut = false;

    public void run() {
        while (!checkOut) {

        }
    }
}
