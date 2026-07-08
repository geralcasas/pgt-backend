package com.portable.microservices.ms_tracking.picking.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.portable.microservices.ms_tracking.picking.infrastructure.persistence.entity.OrdenPickJpaEntity;

public interface OrdenPickJpaRepository extends JpaRepository<OrdenPickJpaEntity, UUID> {
    Page<OrdenPickJpaEntity> findByEstado(String estado, Pageable pageable);
    java.util.Optional<OrdenPickJpaEntity> findByDocRef(String docRef);
}
