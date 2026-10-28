package com.retail.controller;

import com.retail.model.Product;
import com.retail.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Object all(@RequestParam(required = false) String search,
                      @RequestParam(required = false) Integer page,
                      @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            int pageNumber = page == null ? 0 : Math.max(0, page);
            int pageSize = size == null ? 10 : Math.min(100, Math.max(1, size));
            return productService.findPage(search, pageNumber, pageSize);
        }
        return productService.findAll(search);
    }

    @GetMapping("/low-stock")
    public List<Product> lowStock(@RequestParam(defaultValue = "5") int threshold) {
        return productService.findLowStock(threshold);
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable UUID id) {
        return productService.findById(id);
    }

    @PostMapping
    public Product create(@Valid @RequestBody Product product) {
        return productService.create(product);
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable UUID id, @Valid @RequestBody Product request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        productService.delete(id);
    }
}
