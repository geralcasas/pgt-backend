package com.portable.microservices.ms_tracking.picking.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.portable.microservices.ms_tracking.picking.domain.model.DetallePick;
import com.portable.microservices.ms_tracking.picking.domain.model.OrdenPick;
import com.portable.microservices.ms_tracking.picking.domain.ports.out.OrdenPickPersistencePortOut;
import com.portable.microservices.ms_tracking.picking.infrastructure.persistence.mapper.DetallePickPersistenceMapper;
import com.portable.microservices.ms_tracking.picking.infrastructure.persistence.mapper.OrdenPickPersistenceMapper;
import com.portable.microservices.ms_tracking.picking.infrastructure.persistence.repository.DetallePickJpaRepository;
import com.portable.microservices.ms_tracking.picking.infrastructure.persistence.repository.OrdenPickJpaRepository;
import com.portable.microservices.ms_tracking.shared.infrastructure.presentation.PagedResponse;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrdenPickPersistenceAdapter implements OrdenPickPersistencePortOut {
    private final OrdenPickJpaRepository repository;
    private final OrdenPickPersistenceMapper mapper;
    private final DetallePickJpaRepository detalleRepository;
    private final DetallePickPersistenceMapper detalleMapper;

    @Override
    public OrdenPick save(OrdenPick orden) {
        var entity = mapper.toEntity(orden);
        var saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<OrdenPick> findById(UUID idOrden) {
        return repository.findById(idOrden).map(mapper::toDomain);
    }

    @Override
    public Optional<OrdenPick> findByDocRef(String docRef) {
        return repository.findByDocRef(docRef).map(mapper::toDomain);
    }

    @Override
    public PagedResponse<OrdenPick> findAll(int page, int size) {
        var pageable = PageRequest.of(page, size);
        var result = repository.findAll(pageable);
        var items = result.getContent().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
        return new PagedResponse<>(items, result.getTotalElements(), page, size);
    }

    @Override
    public PagedResponse<OrdenPick> findByEstado(String estado, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var result = repository.findByEstado(estado, pageable);
        var items = result.getContent().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
        return new PagedResponse<>(items, result.getTotalElements(), page, size);
    }

    @Override
    public DetallePick save(DetallePick detalle) {
        var entity = detalleMapper.toEntity(detalle);
        var saved = detalleRepository.save(entity);
        return detalleMapper.toDomain(saved);
    }

    @Override
    public List<DetallePick> findDetallesByIdOrden(UUID idOrden) {
        return detalleRepository.findByOrden_IdOrden(idOrden).stream()
                .map(detalleMapper::toDomain)
                .collect(Collectors.toList());
    }
}
