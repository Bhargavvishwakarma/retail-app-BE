package com.retail.repository;

import com.retail.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("select p from Product p where p.active = true and " +
            "(lower(p.name) like lower(concat('%', :search, '%')) or " +
            "lower(p.sku) like lower(concat('%', :search, '%'))) ")
    List<Product> searchActive(@Param("search") String search);

    @Query("select p from Product p where p.active = true and " +
            "(lower(p.name) like lower(concat('%', :search, '%')) or " +
            "lower(p.sku) like lower(concat('%', :search, '%'))) ")
    Page<Product> searchActive(@Param("search") String search, Pageable pageable);

    List<Product> findByActiveTrue();

    Page<Product> findByActiveTrue(Pageable pageable);

    long countByActiveTrue();

    long countByActiveTrueAndStockQuantityLessThan(int threshold);

    List<Product> findByActiveTrueAndStockQuantityLessThan(int threshold);
}
