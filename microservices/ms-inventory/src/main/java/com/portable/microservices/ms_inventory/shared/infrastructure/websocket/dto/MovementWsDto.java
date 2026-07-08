package com.portable.microservices.ms_inventory.shared.infrastructure.websocket.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MovementWsDto(
    UUID id,
    UUID productId,
    String productName,
    String tipo,
    Integer cantidad,
    BigDecimal costoPromedio
) {}
