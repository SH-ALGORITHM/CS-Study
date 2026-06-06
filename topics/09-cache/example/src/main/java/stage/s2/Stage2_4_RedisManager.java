package stage.s2;

import domain.Product;
import domain.ProductRepository;
import infra.MeasurementLog;
import infra.Seeder;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * STAGE 2-4 — CacheManager 만 교체 (ConcurrentMap → Redis).
 *
 * <h3>전제</h3>
 * docker compose up -d 로 Redis 7 띄워둔 상태.
 *
 * <h3>관찰 포인트</h3>
 * <ul>
 *   <li>서비스 코드 (@Cacheable) 한 줄도 안 바뀜 — CacheManager 만 교체</li>
 *   <li>redis-cli 로 확인: docker exec -it cs-study-09-redis redis-cli</li>
 *   <li>keys * — "products::1" 같은 키 보임</li>
 *   <li>TTL / 직렬화 / prefix 설정 분리</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"stage.s2", "domain", "infra"})
@EnableCaching
public class Stage2_4_RedisManager {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(cf)
            .cacheDefaults(config)
            .build();
    }

    @Service
    public static class ProductService {
        private final ProductRepository repo;
        public ProductService(ProductRepository repo) { this.repo = repo; }

        @Cacheable(value = "products", key = "#id")
        public Product findById(Long id) {
            MeasurementLog.marker("[MISS] DB hit — id=" + id);
            return repo.findById(id).orElseThrow();
        }
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Stage2_4_RedisManager.class, args);
        ctx.getBean(Seeder.class).seed(5);
        ProductService svc = ctx.getBean(ProductService.class);

        MeasurementLog.title("STAGE 2-4 — RedisCacheManager (분산 캐시)");
        MeasurementLog.section("같은 id 5 회 — 첫 회만 MISS, 나머지 Redis HIT");
        for (int i = 0; i < 5; i++) svc.findById(1L);

        System.out.println();
        System.out.println("[학습] 서비스 코드 그대로. CacheManager 빈만 교체");
        System.out.println("[확인] docker exec -it cs-study-09-redis redis-cli");
        System.out.println("       > keys *");
        System.out.println("       > get products::1");
        System.out.println("       > ttl products::1");
        System.out.println("[함정] JSON 결과에 \"@class\":\"domain.Product\" 노출 → 클래스 리네임/이동 시 역직렬화 폭발");
        System.out.println("       운영에서는 배포 전 캐시 비우기 또는 DTO + 안전한 직렬화기 검토");
        ctx.close();
    }
}
