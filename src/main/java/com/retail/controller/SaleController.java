package com.retail.controller;

import com.retail.dto.SaleRequest;
import com.retail.model.Sale;
import com.retail.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @GetMapping
    public Object all(@RequestParam(required = false) Integer page,
                      @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            int pageNumber = page == null ? 0 : Math.max(0, page);
            int pageSize = size == null ? 24 : Math.min(100, Math.max(1, size));
            return saleService.findPage(pageNumber, pageSize);
        }
        return saleService.findAll();
    }

    @GetMapping("/{id}")
    public Sale get(@PathVariable UUID id) {
        return saleService.findById(id);
    }

    @PostMapping
    public ResponseEntity<Sale> create(@Valid @RequestBody SaleRequest request) {
        Sale sale = saleService.createSale(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(sale);
    }
}
