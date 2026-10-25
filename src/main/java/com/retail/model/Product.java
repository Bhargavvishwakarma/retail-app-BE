package com.retail.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    private String name;

    @Column(unique = true)
    private String sku;

    private String description;

    private String size;

    private String color;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal price;

    private BigDecimal cost = BigDecimal.ZERO;

    private Integer stockQuantity = 0;

    private Boolean active = true;

    private LocalDateTime createdAt = LocalDateTime.now();
}
