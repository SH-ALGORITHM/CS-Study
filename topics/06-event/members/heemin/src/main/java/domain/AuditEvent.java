package domain;

public record AuditEvent(
    Long productId,
    int quantity
) {
}
