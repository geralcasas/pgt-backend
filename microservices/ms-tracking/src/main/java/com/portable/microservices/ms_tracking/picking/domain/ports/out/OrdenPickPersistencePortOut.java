package com.portable.microservices.ms_tracking.picking.domain.ports.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.portable.microservices.ms_tracking.picking.domain.model.DetallePick;
import com.portable.microservices.ms_tracking.picking.domain.model.OrdenPick;
import com.portable.microservices.ms_tracking.shared.infrastructure.presentation.PagedResponse;

public interface OrdenPickPersistencePortOut {
    OrdenPick save(OrdenPick orden);
    Optional<OrdenPick> findById(UUID idOrden);
    Optional<OrdenPick> findByDocRef(String docRef);
    PagedResponse<OrdenPick> findAll(int page, int size);
    PagedResponse<OrdenPick> findByEstado(String estado, int page, int size);
    DetallePick save(DetallePick detalle);
    List<DetallePick> findDetallesByIdOrden(UUID idOrden);
}