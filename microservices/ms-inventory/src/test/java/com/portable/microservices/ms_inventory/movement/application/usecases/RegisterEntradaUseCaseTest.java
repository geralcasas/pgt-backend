package com.portable.microservices.ms_inventory.movement.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portable.microservices.ms_inventory.kardex.domain.ports.in.FindKardexPortIn;
import com.portable.microservices.ms_inventory.kardex.domain.service.CostoPromedioCalculator;
import com.portable.microservices.ms_inventory.locations.infrastructure.persistence.repository.LocationJpaRepository;
import com.portable.microservices.ms_inventory.lot.infrastructure.persistence.entity.LoteJpaEntity;
import com.portable.microservices.ms_inventory.lot.infrastructure.persistence.repository.LoteJpaRepository;
import com.portable.microservices.ms_inventory.movement.domain.model.Movimiento;
import com.portable.microservices.ms_inventory.movement.domain.ports.out.KardexPersistencePortOut;
import com.portable.microservices.ms_inventory.movement.domain.ports.out.LotePersistencePortOut;
import com.portable.microservices.ms_inventory.movement.domain.ports.out.MovimientoPersistencePortOut;
import com.portable.microservices.ms_inventory.product.infrastructure.persistence.entity.ProductJpaEntity;

@ExtendWith(MockitoExtension.class)
class RegisterEntradaUseCaseTest {
    @Mock
    private MovimientoPersistencePortOut movimientoPersistence;
    @Mock
    private KardexPersistencePortOut kardexPersistence;
    @Mock
    private LotePersistencePortOut lotePersistence;
    @Mock
    private FindKardexPortIn findKardexPortIn;
    @Mock
    private LoteJpaRepository loteRepository;
    @Mock
    private LocationJpaRepository locationRepository;
    private final CostoPromedioCalculator costoPromedioCalculator = new CostoPromedioCalculator();

    @InjectMocks
    private RegisterEntradaUseCase useCase;

    @Test
    void executeDebeRegistrarEntradaYActualizarLote() {
        UUID loteId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        LoteJpaEntity lote = lote(loteId, productId, 6);
        useCase = new RegisterEntradaUseCase(movimientoPersistence, kardexPersistence, lotePersistence,
                costoPromedioCalculator, findKardexPortIn, loteRepository, locationRepository);

        when(lotePersistence.findLoteById(loteId)).thenReturn(Optional.of(lote));
        when(findKardexPortIn.findLastByProductId(productId)).thenReturn(Optional.empty());
        when(movimientoPersistence.save(any(Movimiento.class)))
                .thenReturn(new Movimiento(movementId, loteId, 9L, "INGRESO", 4, null, "Compra", "DOC-2"));

        Movimiento result = useCase.execute(loteId, 9L, 4, "Compra", "DOC-2");

        assertEquals(movementId, result.idMovimiento());
        assertEquals(10, lote.getCantidad());
        verify(kardexPersistence).registrarEntrada(movementId, productId, 4, 0, new BigDecimal("10.0000"));
        verify(lotePersistence).update(lote);
    }

    @Test
    void executeDebeRechazarEntradaConCantidadNula() {
        UUID loteId = UUID.randomUUID();
        when(lotePersistence.findLoteById(loteId)).thenReturn(Optional.of(lote(loteId, UUID.randomUUID(), 6)));

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(loteId, 9L, null, "Compra", "DOC-2"));

        verify(movimientoPersistence, never()).save(any());
    }

    private LoteJpaEntity lote(UUID loteId, UUID productId, int cantidad) {
        ProductJpaEntity product = ProductJpaEntity.builder().id_producto(productId).build();
        LoteJpaEntity lote = new LoteJpaEntity();
        lote.setIdLote(loteId);
        lote.setProducto(product);
        lote.setCantidad(cantidad);
        lote.setCostoUnit(BigDecimal.TEN);
        return lote;
    }
}
