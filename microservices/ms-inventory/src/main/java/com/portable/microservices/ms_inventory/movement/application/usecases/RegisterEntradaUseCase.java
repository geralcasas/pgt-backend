package com.portable.microservices.ms_inventory.movement.application.usecases;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.portable.microservices.ms_inventory.kardex.domain.model.Kardex;
import com.portable.microservices.ms_inventory.kardex.domain.ports.in.FindKardexPortIn;
import com.portable.microservices.ms_inventory.kardex.domain.service.CostoPromedioCalculator;
import com.portable.microservices.ms_inventory.kardex.domain.service.CostoPromedioCalculator.ResultadoCalculoPPP;
import com.portable.microservices.ms_inventory.locations.infrastructure.persistence.entity.LocationJpaEntity;
import com.portable.microservices.ms_inventory.locations.infrastructure.persistence.repository.LocationJpaRepository;
import com.portable.microservices.ms_inventory.lot.infrastructure.persistence.entity.LoteJpaEntity;
import com.portable.microservices.ms_inventory.lot.infrastructure.persistence.repository.LoteJpaRepository;
import com.portable.microservices.ms_inventory.movement.domain.model.Movimiento;

import com.portable.microservices.ms_inventory.movement.domain.ports.in.RegisterEntradaPortIn;
import com.portable.microservices.ms_inventory.movement.domain.ports.out.KardexPersistencePortOut;
import com.portable.microservices.ms_inventory.movement.domain.ports.out.LotePersistencePortOut;
import com.portable.microservices.ms_inventory.movement.domain.ports.out.MovimientoPersistencePortOut;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso para registrar una entrada de inventario.
 * 
 * Crea un registro de movimiento tipo INGRESO y genera un kardex con la cantidad ingresada.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RegisterEntradaUseCase implements RegisterEntradaPortIn {

    private final MovimientoPersistencePortOut movimientoPersistence;
    private final KardexPersistencePortOut kardexPersistence;
    private final LotePersistencePortOut lotePersistence;
    private final CostoPromedioCalculator costoPromedioCalculator;
    private final FindKardexPortIn findKardexPortIn;
    private final LoteJpaRepository loteRepository;
    private final LocationJpaRepository locationRepository;


    @Override
    @Transactional
    public Movimiento execute(UUID idLote, Long idUsuario, Integer cantidad, String motivo, String docRef) {
        log.info("Iniciando registro de entrada para lote: {}, cantidad: {}, usuario: {}", idLote, cantidad, idUsuario);

        // Validar que el lote existe
        LoteJpaEntity lote = lotePersistence.findLoteById(idLote)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado: " + idLote));

        // Validar cantidad
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }

        // Validar capacidad de la locación
        if (lote.getLocacion() != null && lote.getLocacion().getIdLocacion() != null) {
            UUID locId = lote.getLocacion().getIdLocacion();
            LocationJpaEntity location = locationRepository.findById(locId)
                    .orElseThrow(() -> new IllegalArgumentException("Locación no encontrada: " + locId));
            if (location.getCapacidad() != null) {
                long currentTotal = loteRepository.getTotalQtyByLocation(locId);
                if (currentTotal + cantidad > location.getCapacidad()) {
                    throw new IllegalArgumentException(
                            "Capacidad de la locación excedida: " + (currentTotal + cantidad) + " > " + location.getCapacidad());
                }
            }
        }

        // Crear movimiento de tipo INGRESO
        Movimiento movimiento = Movimiento.crearEntrada(
                idLote,
                idUsuario,
                cantidad,
                motivo != null ? motivo : "Entrada registrada",
                docRef
        );

        // Validar reglas de negocio
        if (!movimiento.isValidForCreation()) {
            throw new IllegalArgumentException("Datos inválidos para crear movimiento");
        }

        // Persistir movimiento
        Movimiento movimientoGuardado = movimientoPersistence.save(movimiento);
        log.info("Movimiento guardado con ID: {}", movimientoGuardado.idMovimiento());

        // Registrar entrada en kardex
        BigDecimal costoUnitario = lote.getCostoUnit();
        UUID productId = lote.getProducto().getId_producto();
        Optional<Kardex> ultimoKardex = findKardexPortIn.findLastByProductId(productId);
        ResultadoCalculoPPP resultado = costoPromedioCalculator
                .calcularParaIngreso(ultimoKardex, cantidad, costoUnitario);
        kardexPersistence.registrarEntrada(
                movimientoGuardado.idMovimiento(),
                lote.getProducto().getId_producto(),
                cantidad,
                resultado.stockAnterior(),
                resultado.costoPromNuevo()
        );

        lote.setCantidad(lote.getCantidad() + cantidad);
        lotePersistence.update(lote);
        
        log.info("Entrada registrada exitosamente para lote: {}, cantidad: {}", idLote, cantidad);

        return movimientoGuardado;
    }
}
