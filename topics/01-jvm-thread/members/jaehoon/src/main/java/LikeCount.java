public class LikeCount {
    private int count = 0;

    public void withdraw() {
      count++;
    }

    public int getBalance() {
        return count;
    }

}
