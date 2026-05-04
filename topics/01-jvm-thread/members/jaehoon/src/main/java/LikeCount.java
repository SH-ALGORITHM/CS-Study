public class LikeCount {
    private int count;

    public LikeCount() {
        this.count = 0;
    }

    public boolean withdraw() {
        if ( count < 100 ) {
            count++;
            return true;
        } else  {
            return false;
        }
    }

    public int getBalance() {
        return count;
    }

}
