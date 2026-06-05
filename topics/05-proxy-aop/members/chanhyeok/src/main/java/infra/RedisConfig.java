package infra;

import io.lettuce.core.RedisClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 클라이언트 Spring Bean 등록.
 *
 * <p>3 주차 {@code RedisClientFactory} 의 정적 싱글턴 → 4 주차 학습대로 {@code @Bean(destroyMethod="shutdown")} 한 줄.
 */
@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient() {
        return RedisClient.create("redis://localhost:6379");
    }
}
