package infra;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * Lettuce {@link RedisClient} 를 싱글턴으로 관리하는 팩토리.
 *
 * <h3>왜 싱글턴인가</h3>
 * {@code RedisClient.create()} 는 내부적으로 Netty event loop 를 띄움 — 비싸다.
 * 측정 중 매 요청마다 {@code create} 하면 connection 생성 비용이 락 자체 비용보다 커져서
 * 측정값이 왜곡된다. 2 주차 {@code awaitTermination(60)} timeout 컷처럼
 * 측정 도구가 진짜 비용을 가리는 사고의 일종.
 *
 * <h3>사용 패턴</h3>
 * {@code RedisClient} 는 JVM 라이프타임 동안 1 회 생성. {@code connect()} 만 매 요청마다 새로
 * (Lettuce 의 {@code StatefulRedisConnection} 은 thread-safe 하지만 명시적 close 가 깔끔).
 *
 * <h3>shutdown</h3>
 * {@code main} 끝날 때 {@link #shutdown()} 호출 — Netty event loop 정리.
 * 안 부르면 JVM 종료 안 됨 (daemon thread 가 아니라).
 */
public final class RedisClientFactory {

    private static final String REDIS_URL = "redis://localhost:6379";
    private static final RedisClient CLIENT = RedisClient.create(REDIS_URL);

    private RedisClientFactory() {}

    public static StatefulRedisConnection<String, String> connect() {
        return CLIENT.connect();
    }

    public static void shutdown() {
        CLIENT.shutdown();
    }
}
