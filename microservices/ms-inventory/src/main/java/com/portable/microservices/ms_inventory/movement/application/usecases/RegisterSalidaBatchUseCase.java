package com.portable.microservices.ms_inventory.movement.application.usecases;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portable.microservices.ms_inventory.kardex.infrastructure.persistence.entity.KardexJpaEntity;
import com.portable.microservices.ms_inventory.lot.infrastructure.persistence.entity.LoteJpaEntity;
import com.portable.microservices.ms_inventory.movement.domain.event.MovementCreatedEvent;
import com.portable.microservices.ms_inventory.movement.domain.model.Movimiento;
import com.portable.microservices.ms_inventory.movement.domain.ports.in.RegisterSalidaBatchPortIn;
import com.portable.microservices.ms_inventory.movement.domain.ports.out.KardexPersistencePortOut;
import com.portable.microservices.ms_inventory.movement.domain.ports.out.LotePersistencePortOut;
import com.portable.microservices.ms_inventory.movement.domain.ports.out.MovimientoPersistencePortOut;
import com.portable.microservices.ms_inventory.shared.domain.event.StockDecreasedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterSalidaBatchUseCase implements RegisterSalidaBatchPortIn {

    private final MovimientoPersistencePortOut movimientoPersistence;
    private final KardexPersistencePortOut kardexPersistence;
    private final LotePersistencePortOut lotePersistence;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public List<UUID> execute(List<SalidaBatchItem> items, String motivo, String documentoRef, Long userId) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Debe especificar al menos un item");
        }

        List<UUID> movementIds = new ArrayList<>();

        for (SalidaBatchItem item : items) {
            UUID movementId = processSingleItem(item, motivo, documentoRef, userId);
            movementIds.add(movementId);
        }

        log.info("Salida batch completada: {} movimientos registrados", movementIds.size());
        return movementIds;
    }

    private UUID processSingleItem(SalidaBatchItem item, String motivo, String documentoRef, Long userId) {
        UUID idLote = item.idLote();
        Integer cantidad = item.cantidad();

        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }

        LoteJpaEntity lote = lotePersistence.findLoteById(idLote)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado: " + idLote));

        UUID idProducto = lote.getProducto().getId_producto();

        var ultimoKardex = kardexPersistence.findLastByProductId(idProducto);
        Integer stockDisponible = ultimoKardex.map(KardexJpaEntity::getStockActual).orElse(0);

        if (stockDisponible < cantidad) {
            throw new IllegalArgumentException(
                    "Stock insuficiente para producto " + idProducto
                            + ". Disponible: " + stockDisponible + ", solicitado: " + cantidad);
        }

        Movimiento movimiento = Movimiento.crearSalida(idLote, userId, cantidad,
                motivo != null ? motivo : "Salida por picking", documentoRef);

        if (!movimiento.isValidForCreation()) {
            throw new IllegalArgumentException("Datos inválidos para crear movimiento");
        }

        Movimiento movimientoGuardado = movimientoPersistence.save(movimiento);

        BigDecimal costoPromedio = ultimoKardex.map(KardexJpaEntity::getCostoProm).orElse(BigDecimal.ZERO);
        kardexPersistence.registrarSalida(
                movimientoGuardado.idMovimiento(), idProducto, cantidad, stockDisponible, costoPromedio);

        int remaining = cantidad;
        int fromThisLot = Math.min(remaining, lote.getCantidad());
        lote.setCantidad(lote.getCantidad() - fromThisLot);
        if (lote.getCantidad() == 0) {
            lote.setEstado("AGOTADO");
        }
        lotePersistence.update(lote);
        remaining -= fromThisLot;

        if (remaining > 0) {
            List<LoteJpaEntity> otherLots = lotePersistence.findLotesByProductId(idProducto);
            for (LoteJpaEntity other : otherLots) {
                if (remaining <= 0)
                    break;
                if (other.getIdLote().equals(idLote))
                    continue;
                if (other.getCantidad() <= 0)
                    continue;

                int deduct = Math.min(remaining, other.getCantidad());
                other.setCantidad(other.getCantidad() - deduct);
                if (other.getCantidad() == 0) {
                    other.setEstado("AGOTADO");
                }
                lotePersistence.update(other);
                remaining -= deduct;
            }

            if (remaining > 0) {
                throw new IllegalArgumentException(
                        "Stock insuficiente en lotes. Faltan " + remaining + " unidades");
            }
        }

        UUID locacionId = lote.getLocacion() != null ? lote.getLocacion().getIdLocacion() : null;
        eventPublisher.publishEvent(new MovementCreatedEvent(
                movimientoGuardado.idMovimiento(),
                idProducto,
                movimientoGuardado.tipo(),
                locacionId,
                movimientoGuardado.cantidad(),
                userId));

        Integer nuevoStock = stockDisponible - cantidad;
        eventPublisher.publishEvent(new StockDecreasedEvent(idProducto, nuevoStock));

        return movimientoGuardado.idMovimiento();
    }
}
