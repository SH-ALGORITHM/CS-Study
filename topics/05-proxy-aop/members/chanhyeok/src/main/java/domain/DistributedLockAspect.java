package domain;

import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.lang.reflect.Method;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

/**
 * 분산락 Aspect — 3 주차 보일러플레이트 흡수.
 *
 * <h3>흡수하는 코드 패턴 (3 주차 Stage2Distributed)</h3>
 * <pre>
 * String lockKey = "lock:wallet:" + Math.min(fromId, toId);
 * String lockValue = UUID.randomUUID().toString();
 * try (StatefulRedisConnection&lt;String, String&gt; rconn = RedisClientFactory.connect()) {
 *     RedisCommands&lt;String, String&gt; redis = rconn.sync();
 *     String result = redis.set(lockKey, lockValue, SetArgs.Builder.nx().ex(5));
 *     if (!"OK".equals(result)) {
 *         throw new LockAcquireFailedException(...);
 *     }
 *     try {
 *         // 실제 비즈니스 로직
 *     } finally {
 *         redis.eval(UNLOCK_LUA, ScriptOutputType.INTEGER, ...);   // 본인 락만 안전 해제
 *     }
 * }
 * </pre>
 *
 * <h3>실패 정책</h3>
 * fail-fast — 락 획득 실패 시 즉시 {@link LockAcquireFailedException} 던짐.
 * 호출자가 재시도 결정 (3 주차 트레이드오프 그대로).
 */
@Aspect
@Component
@Order(1)   // Stage4 양파 껍질에서 가장 바깥 — 락 잡은 후 AuditAspect(@Order 2) 동작
public class DistributedLockAspect {

    private static final String UNLOCK_LUA = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
          return redis.call('del', KEYS[1])
        else
          return 0
        end
        """;

    private final RedisClient redisClient;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParserContext templateContext = new TemplateParserContext();   // "#{...}" 템플릿
    private final ParameterNameDiscoverer paramDiscoverer = new DefaultParameterNameDiscoverer();

    public DistributedLockAspect(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @Around("@annotation(distributedLock)")
    public Object lock(ProceedingJoinPoint pjp, DistributedLock distributedLock) throws Throwable {
        String key = "lock:" + resolveKey(pjp, distributedLock.key());
        String value = UUID.randomUUID().toString();

        try (StatefulRedisConnection<String, String> conn = redisClient.connect()) {
            RedisCommands<String, String> redis = conn.sync();

            // 1. SETNX + TTL — 원자적 잠금
            String result = redis.set(key, value,
                SetArgs.Builder.nx().ex(distributedLock.ttlSeconds()));
            if (!"OK".equals(result)) {
                throw new LockAcquireFailedException(key);
            }

            try {
                // 2. 실제 비즈니스 로직 (사용자 메서드)
                return pjp.proceed();
            } finally {
                // 3. Lua script — 본인 락만 안전 해제
                redis.eval(UNLOCK_LUA, ScriptOutputType.INTEGER,
                    new String[]{key}, value);
            }
        }
    }

    /** SpEL 로 메서드 인자 참조해서 키 동적 생성 (예: "wallet:#{fromId}") */
    private String resolveKey(ProceedingJoinPoint pjp, String expression) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
            null, method, pjp.getArgs(), paramDiscoverer);
        // "wallet:#{fromId}" → "wallet:1" (SpEL template 모드)
        return parser.parseExpression(expression, templateContext).getValue(context, String.class);
    }

    public static class LockAcquireFailedException extends RuntimeException {
        public LockAcquireFailedException(String key) {
            super("락 획득 실패 — key=" + key);
        }
    }
}
