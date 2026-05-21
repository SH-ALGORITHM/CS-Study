package infra;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * Redis 분산락 실습용 Lettuce RedisClient를 관리하는 클래스.
 *
 * RedisClient는 내부적으로 Netty 리소스를 만들기 때문에 매번 생성하면 비용이 크다.
 * 따라서 RedisClient는 static 싱글턴으로 한 번만 만들고,
 * 실제 작업 시에는 Redis connection만 열어 사용한다.
 *
 * 반환된 connection은 호출한 쪽에서 닫아야 한다.
 */
public final class RedisClientFactory {

    private static final String REDIS_URL = "redis://localhost:6379";
    private static final RedisClient CLIENT = RedisClient.create(REDIS_URL);

    private RedisClientFactory() {
    }

    /**
     * Redis 작업에 사용할 connection을 새로 연다.
     *
     * RedisClient는 싱글턴으로 재사용하고, connection만 호출 단위로 열어
     * try-with-resources에서 닫는 방식으로 사용한다.
     */
    public static StatefulRedisConnection<String, String> connect() {
        return CLIENT.connect();
    }

    /**
     * 프로그램 종료 시 RedisClient 내부 리소스를 정리한다.
     *
     * Lettuce는 내부적으로 Netty 리소스를 사용하므로 main 종료 전에 호출하지 않으면
     * JVM이 바로 종료되지 않을 수 있다.
     */
    public static void shutdown() {
        CLIENT.shutdown();
    }
}
