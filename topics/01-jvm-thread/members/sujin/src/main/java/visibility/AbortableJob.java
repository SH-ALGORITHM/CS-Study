package visibility;

public interface AbortableJob extends Runnable {
    void abort();
}
