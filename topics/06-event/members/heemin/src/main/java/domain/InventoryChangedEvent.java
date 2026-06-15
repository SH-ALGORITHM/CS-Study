package domain;

public record InventoryChangedEvent(
    Long productId,
    int beforeQty,
    int afterQty
) {
}
