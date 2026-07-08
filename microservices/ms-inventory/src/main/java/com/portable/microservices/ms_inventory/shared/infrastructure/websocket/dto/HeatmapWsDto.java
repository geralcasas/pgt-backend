package com.portable.microservices.ms_inventory.shared.infrastructure.websocket.dto;

import java.util.UUID;

public record HeatmapWsDto(
    UUID locacionId,
    Long idAlmacen,
    long movementCount,
    long dailyPicks,
    int intensity
) {}
