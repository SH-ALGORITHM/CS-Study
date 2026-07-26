package domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 송금 완료 이벤트 — 6 주차 publishEvent payload.
 *
 * <h3>설계 원칙</h3>
 * <ul>
 *   <li>record — 불변 + equals/hashCode/getter 자동 (Spring 4.2+ payload-only)</li>
 *   <li>도메인 전용 타입 — String / Long 같은 공통 타입 발행 시 타입 충돌 위험</li>
 *   <li>풍부한 payload — @Async listener 가 다른 스레드에서도 DB 재조회 없이 처리 가능</li>
 *   <li>과거형 이름 — 이미 일어난 사건. PlaceTransfer 같은 명령형 X</li>
 * </ul>
 *
 * <h3>5 주차 → 6 주차 매핑</h3>
 * <ul>
 *   <li>5 주차 — TransferService.transfer() 안에서 @Audited 가 commit 전 감사 기록</li>
 *   <li>6 주차 — transfer() 끝에 publishEvent + AFTER_COMMIT listener 가 commit 후 감사</li>
 * </ul>
 */
public record TransferCompletedEvent(
    long fromId,
    long toId,
    BigDecimal amount,
    Instant completedAt
) {}
