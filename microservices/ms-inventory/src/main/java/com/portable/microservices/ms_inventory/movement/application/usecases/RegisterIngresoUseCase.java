package com.portable.microservices.ms_inventory.movement.application.usecases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portable.microservices.ms_inventory.kardex.domain.model.Kardex;
import com.portable.microservices.ms_inventory.kardex.domain.ports.out.KardexPersistencePortOut;
import com.portable.microservices.ms_inventory.kardex.domain.ports.out.LotPersistencePortOut;
import com.portable.microservices.ms_inventory.kardex.domain.service.CostoPromedioCalculator;
import com.portable.microservices.ms_inventory.locations.infrastructure.persistence.entity.LocationJpaEntity;
import com.portable.microservices.ms_inventory.locations.infrastructure.persistence.repository.LocationJpaRepository;
import com.portable.microservices.ms_inventory.lot.domain.model.Lot;
import com.portable.microservices.ms_inventory.lot.infrastructure.persistence.repository.LoteJpaRepository;
import com.portable.microservices.ms_inventory.movement.domain.event.MovementCreatedEvent;
import com.portable.microservices.ms_inventory.movement.domain.model.Movement;
import com.portable.microservices.ms_inventory.movement.domain.model.TipoMovimiento;
import com.portable.microservices.ms_inventory.movement.domain.ports.in.RegisterIngresoPortIn;
import com.portable.microservices.ms_inventory.movement.domain.ports.out.MovementPersistencePortOut;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegisterIngresoUseCase implements RegisterIngresoPortIn {
    private final LotPersistencePortOut lotPersistence;
    private final MovementPersistencePortOut movementPersistence;
    private final KardexPersistencePortOut kardexPersistence;
    private final CostoPromedioCalculator costoPromedioCalculator;
    private final LoteJpaRepository loteRepository;
    private final LocationJpaRepository locationRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void execute(RegisterIngresoCommand command) {

        // ============================================
        // PASO 1: Validaciones básicas
        // ============================================
        if (command.cantidad() == null || command.cantidad() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }
        if (command.costoUnit() == null || command.costoUnit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El costo unitario debe ser mayor a 0");
        }
        if (command.productId() == null) {
            throw new IllegalArgumentException("El producto es requerido");
        }
        if (command.locationId() != null) {
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
        Lot lot = new Lot(
            null,
            command.productId(),
            command.locationId(),
            command.nroLote(),
            command.fecIngreso() != null ? command.fecIngreso() : LocalDate.now(),
            command.costoUnit(),
            "DISPONIBLE",
            command.proveedor(),
            command.codProv(),
            command.cantidad()
        );

        Lot lotGuardado = lotPersistence.save(lot);
        Movement movimiento = new Movement(
            null,
            lotGuardado.id(),
            command.userId(),
            TipoMovimiento.INGRESO,
            command.cantidad(),
            OffsetDateTime.now(),
            command.motivo(),
            command.docRef()
        );

        Movement movimientoGuardado = movementPersistence.save(movimiento);
        // ============================================
        // PASO 4: Calcular PPP (Costo Promedio Ponderado)
        // ============================================
        Optional<Kardex> ultimoKardex = kardexPersistence.findLastByProductId(command.productId());

        CostoPromedioCalculator.ResultadoCalculoPPP resultadoPPP =
            costoPromedioCalculator.calcularParaIngreso(
                ultimoKardex,
                command.cantidad(),
                command.costoUnit()
            );
        Kardex kardex = new Kardex(
            null,
            movimientoGuardado.id(),
            command.productId(),
            resultadoPPP.stockAnterior(),
            command.cantidad(),
            0,
            resultadoPPP.stockActual(),
            resultadoPPP.costoPromNuevo()
        );

        kardexPersistence.save(kardex);
        // ============================================
        // PASO 6: Publicar evento
        // ============================================
        eventPublisher.publishEvent(new MovementCreatedEvent(
            movimientoGuardado.id(),
            command.productId(),
            TipoMovimiento.INGRESO.name(),
            command.locationId(),
            command.cantidad(),
            command.userId()
        ));
    }
}
