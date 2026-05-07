import java.util.concurrent.atomic.AtomicInteger;

public class AtomicConcertBooking implements ConcertBooking {
    private final AtomicInteger availableSeats;

    public AtomicConcertBooking() { this(100); }

    public AtomicConcertBooking(int initialSeats) {
        this.availableSeats = new AtomicInteger(initialSeats);
    }

    @Override
    public boolean reserve() {
        int current;
        do {
            current = availableSeats.get();
            if (current <= 0) return false;
        } while (!availableSeats.compareAndSet(current, current - 1));
        return true;
    }
}
