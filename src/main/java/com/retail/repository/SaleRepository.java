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
            "where s.saleDate >= :from " +
            "and s.saleDate < :toExclusive " +
            "and (:search = '' or lower(coalesce(s.saleNumber, '')) like lower(concat('%', :search, '%')) " +
            "or lower(coalesce(c.name, '')) like lower(concat('%', :search, '%')) " +
            "or lower(coalesce(c.phone, '')) like lower(concat('%', :search, '%')) " +
            "or lower(coalesce(c.email, '')) like lower(concat('%', :search, '%')))",
            countQuery = "select count(s) from Sale s left join s.customer c " +
                    "where s.saleDate >= :from " +
                    "and s.saleDate < :toExclusive " +
                    "and (:search = '' or lower(coalesce(s.saleNumber, '')) like lower(concat('%', :search, '%')) " +
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

    @Query("select s.id from Sale s order by s.saleDate desc")
    List<UUID> findRecentSaleIds(Pageable pageable);

    @Query("select distinct s from Sale s " +
            "left join fetch s.customer " +
            "left join fetch s.items i " +
            "left join fetch i.product p " +
            "left join fetch p.category " +
            "where s.id in :ids")
    List<Sale> findAllWithDetailsByIdIn(@Param("ids") List<UUID> ids);
}
