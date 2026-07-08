package com.portable.microservices.ms_inventory.movement.application.usecases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portable.microservices.ms_inventory.kardex.domain.model.Kardex;
import com.portable.microservices.ms_inventory.kardex.domain.ports.out.KardexPersistencePortOut;
import com.portable.microservices.ms_inventory.kardex.domain.service.CostoPromedioCalculator;
import com.portable.microservices.ms_inventory.locations.infrastructure.persistence.entity.LocationJpaEntity;
import com.portable.microservices.ms_inventory.locations.infrastructure.persistence.repository.LocationJpaRepository;
import com.portable.microservices.ms_inventory.lot.infrastructure.persistence.entity.LoteJpaEntity;
import com.portable.microservices.ms_inventory.lot.infrastructure.persistence.repository.LoteJpaRepository;
import com.portable.microservices.ms_inventory.movement.domain.event.MovementCreatedEvent;
import com.portable.microservices.ms_inventory.movement.domain.model.Movement;
import com.portable.microservices.ms_inventory.movement.domain.model.TipoMovimiento;
import com.portable.microservices.ms_inventory.shared.domain.event.StockDecreasedEvent;
import com.portable.microservices.ms_inventory.movement.domain.ports.in.RegisterMovementPortIn;
import com.portable.microservices.ms_inventory.movement.domain.ports.out.MovementPersistencePortOut;
import com.portable.microservices.ms_inventory.product.infrastructure.persistence.entity.ProductJpaEntity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegisterMovementUseCase implements RegisterMovementPortIn {
    private final MovementPersistencePortOut movementPersistence;
    private final KardexPersistencePortOut kardexPersistence;
    private final CostoPromedioCalculator costoPromedioCalculator;
    private final LoteJpaRepository loteRepository;
    private final LocationJpaRepository locationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void execute(RegisterMovementCommand command) {
        if (command.cantidad() == null || command.cantidad() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }
        if (command.tipo() == null || command.tipo().isBlank()) {
            throw new IllegalArgumentException("El tipo de movimiento es requerido");
        }
        if (command.productId() == null) {
            throw new IllegalArgumentException("El producto es requerido");
        }

        String upperTipo = command.tipo().toUpperCase();
        if ("AJUSTE".equals(upperTipo)) {
            upperTipo = "AJUSTE_POSITIVO";
        }
        TipoMovimiento tipo;
        try {
            tipo = TipoMovimiento.valueOf(upperTipo);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de movimiento inválido: " + command.tipo());
        }

        if ((tipo == TipoMovimiento.INGRESO || tipo == TipoMovimiento.AJUSTE_POSITIVO)
                && (command.costoUnit() == null || command.costoUnit().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("El costo unitario debe ser mayor a 0 para INGRESO/AJUSTE_POSITIVO");
        }

        if (command.locationId() != null && (tipo == TipoMovimiento.INGRESO || tipo == TipoMovimiento.AJUSTE_POSITIVO)) {
            LocationJpaEntity location = locationRepository.findById(command.locationId())
                    .orElseThrow(() -> new IllegalArgumentException("Locación no encontrada: " + command.locationId()));
            if (location.getCapacidad() != null) {
                long currentTotal = loteRepository.getTotalQtyByLocation(command.locationId());
                if (currentTotal + command.cantidad() > location.getCapacidad()) {
                    throw new IllegalArgumentException(
                            "Capacidad de la locación excedida: " + (currentTotal + command.cantidad()) + " > " + location.getCapacidad());
                }
            }
        }

        UUID resolvedLotId = command.lotId();

        if (resolvedLotId == null && (tipo == TipoMovimiento.INGRESO || tipo == TipoMovimiento.AJUSTE_POSITIVO)) {
            LoteJpaEntity lote = new LoteJpaEntity();
            ProductJpaEntity productRef = new ProductJpaEntity();
            productRef.setId_producto(command.productId());
            lote.setProducto(productRef);
            if (command.locationId() != null) {
                LocationJpaEntity locRef = new LocationJpaEntity();
                locRef.setIdLocacion(command.locationId());
                lote.setLocacion(locRef);
            }

            String nroLote = (command.nroLote() != null && !command.nroLote().isBlank())
                    ? command.nroLote()
                    : "AUTO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            lote.setNroLote(nroLote);
            lote.setFecIngreso(LocalDate.now());
            lote.setCostoUnit(command.costoUnit() != null ? command.costoUnit() : BigDecimal.ZERO);
            lote.setProveedor(command.proveedor());
            lote.setCantidad(command.cantidad());
            lote.setEstado("DISPONIBLE");
            resolvedLotId = loteRepository.save(lote).getIdLote();
        }

        UUID locacionFromDeduction = null;

        if ((tipo == TipoMovimiento.SALIDA || tipo == TipoMovimiento.AJUSTE_NEGATIVO) && command.productId() != null) {
            List<LoteJpaEntity> lots = loteRepository.findByProductoIdOrderByFecIngresoAsc(command.productId());
            int remaining = command.cantidad();
            for (LoteJpaEntity lot : lots) {
                if (remaining <= 0)
                    break;
                if (lot.getCantidad() <= 0)
                    continue;
                if (locacionFromDeduction == null && lot.getLocacion() != null) {
                    locacionFromDeduction = lot.getLocacion().getIdLocacion();
                }
                int deduct = Math.min(remaining, lot.getCantidad());
                lot.setCantidad(lot.getCantidad() - deduct);
                if (lot.getCantidad() == 0) {
                    lot.setEstado("AGOTADO");
                }
                loteRepository.save(lot);
                remaining -= deduct;
            }
            if (remaining > 0) {
                throw new IllegalArgumentException(
                        "Stock insuficiente en lotes para " + tipo + ". Faltan " + remaining + " unidades");
            }
        }

        Movement movement = new Movement(
                null,
                resolvedLotId,
                command.userId(),
                tipo,
                command.cantidad(),
                OffsetDateTime.now(),
                command.motivo(),
                command.docRef());
        Movement saved = movementPersistence.save(movement);

        if (command.productId() != null) {
            Optional<Kardex> ultimoKardex = kardexPersistence.findLastByProductId(command.productId());
            CostoPromedioCalculator.ResultadoCalculoPPP resultado;
            if (tipo == TipoMovimiento.INGRESO || tipo == TipoMovimiento.AJUSTE_POSITIVO) {
                resultado = costoPromedioCalculator.calcularParaIngreso(
                        ultimoKardex,
                        command.cantidad(),
                        command.costoUnit() != null ? command.costoUnit() : BigDecimal.ZERO);
            } else {
                resultado = costoPromedioCalculator.calcularParaSalida(
                        ultimoKardex,
                        command.cantidad());
            }

            int cantIngreso = (tipo == TipoMovimiento.INGRESO || tipo == TipoMovimiento.AJUSTE_POSITIVO)
                    ? command.cantidad()
                    : 0;
            int cantSalida = (tipo == TipoMovimiento.SALIDA || tipo == TipoMovimiento.EGRESO
                    || tipo == TipoMovimiento.AJUSTE_NEGATIVO)
                            ? command.cantidad()
                            : 0;
            Kardex kardex = new Kardex(
                    null,
                    saved.id(),
                    command.productId(),
                    resultado.stockAnterior(),
                    cantIngreso,
                    cantSalida,
                    resultado.stockActual(),
                    resultado.costoPromNuevo());
            UUID locacionId;
            if (tipo == TipoMovimiento.SALIDA || tipo == TipoMovimiento.AJUSTE_NEGATIVO) {
                locacionId = locacionFromDeduction;
                if (locacionId == null && resolvedLotId != null) {
                    locacionId = loteRepository.findById(resolvedLotId)
                            .map(l -> l.getLocacion() != null ? l.getLocacion().getIdLocacion() : null)
                            .orElse(null);
                }
            } else {
                locacionId = command.locationId();
            }
            eventPublisher.publishEvent(new MovementCreatedEvent(
                    saved.id(),
                    command.productId(),
                    tipo.name(),
                    locacionId,
                    command.cantidad(),
                    command.userId()));
            if (tipo == TipoMovimiento.SALIDA || tipo == TipoMovimiento.AJUSTE_NEGATIVO) {
                eventPublisher.publishEvent(new StockDecreasedEvent(command.productId(), resultado.stockActual()));
            }
            kardexPersistence.save(kardex);
        }
    }
}