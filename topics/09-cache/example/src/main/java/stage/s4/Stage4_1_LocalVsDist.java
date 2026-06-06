package stage.s4;

import com.github.benmanes.caffeine.cache.Caffeine;
import domain.Product;
import domain.ProductRepository;
import infra.MeasurementLog;
import infra.Seeder;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

/**
 * STAGE 4-1 — Caffeine (로컬, μs) vs Redis (분산, ms) 측정.
 *
 * <h3>접근 방식 — CacheManager 직접 주입</h3>
 * @Cacheable 에는 cacheManager 속성이 없음 (cacheResolver 만 있음).
 * 한 클래스에서 두 매니저 비교하려면 CacheManager / Cache 직접 주입해서 get/put 호출.
 * 측정 목적엔 AOP 프록시 오버헤드도 빠져서 순수 캐시 접근 시간 더 정확.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>Caffeine — JVM 안 → 마이크로초 단위. 다중 인스턴스 동기화 X</li>
 *   <li>Redis — 네트워크 경유 → 밀리초 단위. 모든 인스턴스 공유</li>
 *   <li>실무 — L1 (Caffeine) + L2 (Redis) 하이브리드</li>
 * </ul>
 *
 * <h3>전제</h3>
 * docker compose up -d 로 Redis 띄워둔 상태.
 */
@SpringBootApplication(scanBasePackages = {"stage.s4", "domain", "infra"})
public class Stage4_1_LocalVsDist {

    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager("caffeine-products");
        mgr.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
        );
        return mgr;
    }

    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory cf) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        return RedisCacheManager.builder(cf).cacheDefaults(config).build();
    }

    @Service
    public static class BenchmarkService {
        private final ProductRepository repo;
        private final Cache caffeine;
        private final Cache redis;

        public BenchmarkService(ProductRepository repo,
                                CacheManager caffeineCacheManager,
                                CacheManager redisCacheManager) {
            this.repo = repo;
            this.caffeine = caffeineCacheManager.getCache("caffeine-products");
            this.redis = redisCacheManager.getCache("redis-products");
        }

        public Product getFromCaffeine(Long id) {
            Product cached = caffeine.get(id, Product.class);
            if (cached != null) return cached;
            Product fresh = repo.findById(id).orElseThrow();
            caffeine.put(id, fresh);
            return fresh;
        }

        public Product getFromRedis(Long id) {
            Product cached = redis.get(id, Product.class);
            if (cached != null) return cached;
            Product fresh = repo.findById(id).orElseThrow();
            redis.put(id, fresh);
            return fresh;
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage4_1_LocalVsDist.class, args);
        ctx.getBean(Seeder.class).seed(10);
        BenchmarkService svc = ctx.getBean(BenchmarkService.class);

        MeasurementLog.title("STAGE 4-1 — Caffeine vs Redis 측정 (HIT 시)");

        // 워밍업 (첫 회 MISS, 측정 제외)
        svc.getFromCaffeine(1L);
        svc.getFromRedis(1L);

        // 측정 — HIT 1000 회
        int n = 1000;
        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) svc.getFromCaffeine(1L);
        long caffeineNs = (System.nanoTime() - t1) / n;

        long t2 = System.nanoTime();
        for (int i = 0; i < n; i++) svc.getFromRedis(1L);
        long redisNs = (System.nanoTime() - t2) / n;

        System.out.println();
        System.out.println("[측정] HIT 시 평균 응답 시간");
        System.out.println("  Caffeine = " + caffeineNs + " ns (" + caffeineNs / 1000.0 + " μs)");
        System.out.println("  Redis    = " + redisNs + " ns (" + redisNs / 1000.0 + " μs)");
        System.out.println("  배수     = " + (redisNs / Math.max(caffeineNs, 1)) + " 배");

        System.out.println();
        System.out.println("[학습] Caffeine μs / Redis ms — 보통 100 ~ 1000 배 차이");
        System.out.println("       Caffeine 빠르나 다중 인스턴스 동기화 X");
        System.out.println("       Redis 느리나 공유. L1+L2 하이브리드가 실무 답");
        ctx.close();
    }
}
