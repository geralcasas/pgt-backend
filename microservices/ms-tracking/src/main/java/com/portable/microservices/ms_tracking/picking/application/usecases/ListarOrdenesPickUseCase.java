package com.portable.microservices.ms_tracking.picking.application.usecases;

import org.springframework.stereotype.Service;

import com.portable.microservices.ms_tracking.picking.domain.model.OrdenPick;
import com.portable.microservices.ms_tracking.picking.domain.ports.in.ListarOrdenesPickPortIn;
import com.portable.microservices.ms_tracking.picking.domain.ports.out.OrdenPickPersistencePortOut;
import com.portable.microservices.ms_tracking.shared.infrastructure.presentation.PagedResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ListarOrdenesPickUseCase implements ListarOrdenesPickPortIn {
    private final OrdenPickPersistencePortOut ordenPickPersistence;

    @Override
    public PagedResponse<OrdenPick> execute(int page, int size) {
        return ordenPickPersistence.findAll(page, size);
    }

    @Override
    public PagedResponse<OrdenPick> executeByEstado(String estado, int page, int size) {
        return ordenPickPersistence.findByEstado(estado, page, size);
    }
    
}
