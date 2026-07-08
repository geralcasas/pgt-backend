package com.portable.microservices.ms_inventory.product.application.usercases;

import com.portable.microservices.ms_inventory.product.domain.model.Product;
import com.portable.microservices.ms_inventory.product.domain.ports.in.DeleteProductPortIn;
import com.portable.microservices.ms_inventory.product.domain.ports.out.ProductPersistencePortOut;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteProductUseCase implements DeleteProductPortIn {

    private final ProductPersistencePortOut productPersistence;

    @Override
    public void delete(UUID id) {
        Product product = productPersistence.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + id));

        Product deactivated = new Product(
                product.id(), product.categoryId(), product.brandId(),
                product.codProd(), product.codAnexo(), product.descripcion(),
                product.modelosCompatibles(), product.preCom(), product.preVen(),
                false,
                product.fecCreacion(), product.stockMinimo(), product.stockTotal()
        );
        productPersistence.save(deactivated);
    }
}
