public class ConcertBooking {
    private int availableSeats = 100;

    public boolean reserve() {
        if (availableSeats > 0) {
            availableSeats--;
            return true;
        }
        return false;
    }
}
