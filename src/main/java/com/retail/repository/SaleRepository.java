package com.retail.repository;

import com.retail.model.Sale;
import com.retail.dto.SaleSummaryResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    @Query(value = "select new com.retail.dto.SaleSummaryResponse(s.id, s.saleNumber, s.saleDate, c.name, s.paymentMethod, s.totalAmount) " +
            "from Sale s left join s.customer c " +
            "where (:from is null or s.saleDate >= :from) " +
            "and (:toExclusive is null or s.saleDate < :toExclusive) " +
            "and (:search is null or lower(coalesce(s.saleNumber, '')) like lower(concat('%', :search, '%')) " +
            "or lower(coalesce(c.name, '')) like lower(concat('%', :search, '%')) " +
            "or lower(coalesce(c.phone, '')) like lower(concat('%', :search, '%')) " +
            "or lower(coalesce(c.email, '')) like lower(concat('%', :search, '%')))",
            countQuery = "select count(s) from Sale s left join s.customer c " +
                    "where (:from is null or s.saleDate >= :from) " +
                    "and (:toExclusive is null or s.saleDate < :toExclusive) " +
                    "and (:search is null or lower(coalesce(s.saleNumber, '')) like lower(concat('%', :search, '%')) " +
                    "or lower(coalesce(c.name, '')) like lower(concat('%', :search, '%')) " +
                    "or lower(coalesce(c.phone, '')) like lower(concat('%', :search, '%')) " +
                    "or lower(coalesce(c.email, '')) like lower(concat('%', :search, '%')))")
    Page<SaleSummaryResponse> findSummariesFiltered(@Param("from") LocalDateTime from,
                                                    @Param("toExclusive") LocalDateTime toExclusive,
                                                    @Param("search") String search,
                                                    Pageable pageable);

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.saleDate >= :start AND s.saleDate < :end")
    BigDecimal sumTotalBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countBySaleDateBetween(LocalDateTime start, LocalDateTime end);

    List<Sale> findTop5ByOrderBySaleDateDesc();
}
