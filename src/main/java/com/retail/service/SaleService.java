package com.retail.service;

import com.retail.dto.SaleRequest;
import com.retail.dto.SaleSummaryResponse;
import com.retail.model.*;
import com.retail.repository.CustomerRepository;
import com.retail.repository.ProductRepository;
import com.retail.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public List<Sale> findAll() {
        return saleRepository.findAll(Sort.by(Sort.Direction.DESC, "saleDate"));
    }

    public Page<SaleSummaryResponse> findPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "saleDate"));
        return saleRepository.findSummaries(pageable);
    }

    public Page<SaleSummaryResponse> findPage(int page, int size, LocalDate from, LocalDate to) {
        if (from == null && to == null) return findPage(page, size);
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("A valid start and end date are required");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "saleDate"));
        return saleRepository.findSummariesBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), pageable);
    }

    public Sale findById(UUID id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found"));
    }

    @Transactional
    public Sale createSale(SaleRequest request) {
        Sale sale = new Sale();
        sale.setSaleNumber("S-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000));
        sale.setPaymentMethod(request.getPaymentMethod());
        sale.setSaleDate(LocalDateTime.now());

        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + request.getCustomerId()));
            sale.setCustomer(customer);
        }

        BigDecimal total = BigDecimal.ZERO;

        for (SaleRequest.Item itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemRequest.getProductId()));

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new IllegalArgumentException("Product is inactive: " + product.getName());
            }
            if (product.getStockQuantity() < itemRequest.getQuantity()) {
                throw new IllegalArgumentException("Insufficient stock for " + product.getName()
                        + " (available: " + product.getStockQuantity() + ")");
            }

            BigDecimal unitPrice = itemRequest.getUnitPrice() == null ? product.getPrice() : itemRequest.getUnitPrice();
            BigDecimal cost = product.getCost() == null ? BigDecimal.ZERO : product.getCost();
            BigDecimal minimumPrice = cost.multiply(new BigDecimal("1.15")).setScale(2, RoundingMode.CEILING);
            if (unitPrice == null || unitPrice.compareTo(minimumPrice) < 0) {
                throw new IllegalArgumentException("Price for " + product.getName() + " must be at least Rs. " + minimumPrice + " (cost plus 15% profit)");
            }

            product.setStockQuantity(product.getStockQuantity() - itemRequest.getQuantity());
            productRepository.save(product);

            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProduct(product);
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(unitPrice);
            item.setSubtotal(unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
            sale.getItems().add(item);

            total = total.add(item.getSubtotal());
        }

        sale.setTotalAmount(total);
        return saleRepository.save(sale);
    }
}
