package domain;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(2)
public class CacheAspect {

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    @Around("@annotation(cached)")
    public Object around(ProceedingJoinPoint pjp, Cached cached) throws Throwable {
        String key = pjp.getSignature().toShortString() + Arrays.deepToString(pjp.getArgs());
        long now = System.nanoTime();
        Entry entry = cache.get(key);

        if (entry != null && entry.expiresAtNanos > now) {
            System.out.println("[CACHE] hit — " + key);
            return entry.value;
        }

        System.out.println("[CACHE] miss — " + key);
        Object result = pjp.proceed();
        long ttlNanos = cached.ttlSeconds() * 1_000_000_000L;
        cache.put(key, new Entry(result, now + ttlNanos));
        System.out.println("[CACHE] save — ttl=" + cached.ttlSeconds() + "s");
        return result;
    }

    private record Entry(Object value, long expiresAtNanos) {
    }
}
