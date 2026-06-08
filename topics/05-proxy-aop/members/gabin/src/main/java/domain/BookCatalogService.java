package domain;

import org.springframework.stereotype.Service;

@Service
public class BookCatalogService {

    private int dbQueryCount = 0;

    @Cached(ttlSeconds = 60)
    public String findBookTitle(long bookId) {
        dbQueryCount++;
        sleepSlowQuery();
        return "book-" + bookId;
    }

    public int dbQueryCount() {
        return dbQueryCount;
    }

    private void sleepSlowQuery() {
        try {
            Thread.sleep(120);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
