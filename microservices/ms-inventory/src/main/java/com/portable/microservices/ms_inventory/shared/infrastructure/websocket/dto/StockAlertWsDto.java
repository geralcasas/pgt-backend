package com.portable.microservices.ms_inventory.shared.infrastructure.websocket.dto;

import java.util.UUID;

public record StockAlertWsDto(
    UUID productId,
    String productName,
    Integer currentStock,
    Integer minStock
) {}
