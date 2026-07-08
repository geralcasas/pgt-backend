package com.portable.microservices.ms_inventory.product.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "producto",
        schema = "inventory",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_code", columnNames = "cod_prod")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductJpaEntity {

    @Id
    @Column(name = "id_producto", updatable = false, nullable = false)
    private UUID id_producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "id_categoria",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_cat_producto")
    )
    private CategoryJpaEntity id_categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "id_marca",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_marca_producto")
    )
    private BrandJpaEntity id_marca;

    @Column(name = "cod_prod", nullable = false, length = 30)
    private String cod_prod;

    @Column(name = "cod_anexo", length = 30)
    private String cod_anexo;

    @Column(name = "descripcion", nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modelos_compatibles", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> modelos_compatibles = List.of();

    @Column(name = "pre_com", precision = 12, scale = 4, nullable = false)
    private BigDecimal pre_com;

    @Column(name = "pre_ven", precision = 12, scale = 4, nullable = false)
    private BigDecimal pre_ven;

    @Column(name = "estado")
    private Boolean estado;

    @Column(name = "fec_creacion", updatable = false)
    private OffsetDateTime fec_creacion;

    @Column(name = "stock_minimo", nullable = false)
    private Integer stock_minimo;
}