public class UnsafeConcertBooking implements ConcertBooking {
    private int availableSeats;

    public UnsafeConcertBooking() {
        this(100);
    }

    public UnsafeConcertBooking(int initialSeats) {
        this.availableSeats = initialSeats;
    }

    @Override
    public boolean reserve() {
        if (availableSeats > 0) {
            availableSeats--;
            return true;
        }
        return false;
    }
}
