package com.portable.microservices.ms_tracking.picking.domain.ports.in;

import com.portable.microservices.ms_tracking.picking.domain.model.OrdenPick;
import com.portable.microservices.ms_tracking.shared.infrastructure.presentation.PagedResponse;

public interface ListarOrdenesPickPortIn {
    PagedResponse<OrdenPick> execute(int page, int size);
    PagedResponse<OrdenPick> executeByEstado(String estado, int page, int size);
}