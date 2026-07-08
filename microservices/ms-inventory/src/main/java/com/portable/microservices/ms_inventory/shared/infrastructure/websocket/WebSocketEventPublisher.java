package com.portable.microservices.ms_inventory.shared.infrastructure.websocket;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.portable.microservices.ms_inventory.kardex.infrastructure.persistence.entity.KardexJpaEntity;
import com.portable.microservices.ms_inventory.kardex.infrastructure.persistence.repository.KardexJpaRepository;
import com.portable.microservices.ms_inventory.locations.infrastructure.persistence.entity.LocationJpaEntity;
import com.portable.microservices.ms_inventory.locations.infrastructure.persistence.repository.LocationJpaRepository;
import com.portable.microservices.ms_inventory.lot.infrastructure.persistence.repository.LoteJpaRepository;
import com.portable.microservices.ms_inventory.movement.domain.event.MovementCreatedEvent;
import com.portable.microservices.ms_inventory.movement.infrastructure.persistence.repository.MovimientoJpaRepository;
import com.portable.microservices.ms_inventory.product.infrastructure.persistence.entity.ProductJpaEntity;
import com.portable.microservices.ms_inventory.product.infrastructure.persistence.repository.ProductJpaRepository;
import com.portable.microservices.ms_inventory.shared.infrastructure.websocket.dto.HeatmapWsDto;
import com.portable.microservices.ms_inventory.shared.infrastructure.websocket.dto.MovementWsDto;
import com.portable.microservices.ms_inventory.shared.infrastructure.websocket.dto.StockAlertWsDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;
    private final LoteJpaRepository loteJpaRepository;
    private final MovimientoJpaRepository movimientoJpaRepository;
    private final LocationJpaRepository locationJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final KardexJpaRepository kardexJpaRepository;

    public void publishMovementCreated(MovementCreatedEvent event) {
        MovementWsDto dto = new MovementWsDto(
            event.movementId(),
            event.productId(),
            getProductName(event.productId()),
            event.tipoMovimiento(),
            event.cantidad(),
            getCostoPromedio(event.productId())
        );
        messagingTemplate.convertAndSend("/topic/inventory/movements", dto);
    }
    public void publishStockAlert(UUID productId, Integer currentStock, Integer minStock) {
        StockAlertWsDto dto = new StockAlertWsDto(productId, getProductName(productId), currentStock, minStock);
        messagingTemplate.convertAndSend("/topic/inventory/stock-alerts", dto);
    }
    public void publishDashboardRefresh() {
        messagingTemplate.convertAndSend("/topic/inventory/dashboard/refresh", "refresh");
    }
    public void publishHeatmapUpdate(HeatmapWsDto dto) {
        messagingTemplate.convertAndSend("/topic/heatmap", dto);
    }
    public void publishHeatmapForMovement(UUID locacionId) {
        try {
            LocationJpaEntity location = locationJpaRepository.findById(locacionId).orElse(null);
            if (location == null) {
                log.warn("Locacion no encontrada para heatmap update: {}", locacionId);
                return;
            }
            long stockQty = loteJpaRepository.getTotalQtyByLocation(locacionId);
            long dailyPicks = movimientoJpaRepository.countDailyMovementsByLocation(locacionId);
            int capacity = location.getCapacidad() != null ? location.getCapacidad() : 0;
            int intensity = calculateIntensity(stockQty, capacity, stockQty);
            HeatmapWsDto dto = new HeatmapWsDto(locacionId, location.getIdAlmacen(), stockQty, dailyPicks, intensity);
            publishHeatmapUpdate(dto);
            log.debug("Heatmap publicado para locacion {}: stock={}, dailyPicks={}, intensity={}",
                locacionId, stockQty, dailyPicks, intensity);
        } catch (Exception e) {
            log.error("Error al publicar heatmap para locacion {}: {}", locacionId, e.getMessage());
        }
    }
    private String getProductName(UUID productId) {
        return productJpaRepository.findById(productId)
                .map(ProductJpaEntity::getDescripcion)
                .orElse("Producto");
    }

    private BigDecimal getCostoPromedio(UUID productId) {
        return kardexJpaRepository.findTopByProductoIdOrderByIdKardexDesc(productId)
                .map(KardexJpaEntity::getCostoProm)
                .orElse(BigDecimal.ZERO);
    }

    private int calculateIntensity(long movementCount, int capacity, long maxMovement) {
        if (movementCount == 0) return 0;
        if (capacity > 0) {
            int pct = (int) Math.min(100, Math.round((double) movementCount / capacity * 100));
            return Math.max(0, Math.min(100, pct));
        }
        if (maxMovement > 0) {
            int pct = (int) Math.round((double) movementCount / maxMovement * 100);
            return Math.max(1, Math.min(100, pct));
        }
        return (int) Math.min(100, movementCount);
    }
}