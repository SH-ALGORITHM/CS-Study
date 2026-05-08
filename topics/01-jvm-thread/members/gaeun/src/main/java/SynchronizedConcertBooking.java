public class SynchronizedConcertBooking implements ConcertBooking {
    private int availableSeats;

    public SynchronizedConcertBooking() {
        this(100);
    }

    public SynchronizedConcertBooking(int initialSeats) {
        this.availableSeats = initialSeats;
    }

    @Override
    public synchronized boolean reserve() {
        if (availableSeats > 0) {
            availableSeats--;
            return true;
        }
        return false;
    }
}
