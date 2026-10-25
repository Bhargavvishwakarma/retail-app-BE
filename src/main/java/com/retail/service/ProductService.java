package com.retail.service;

import com.retail.model.Product;
import com.retail.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<Product> findAll(String search) {
        if (search != null && !search.isBlank()) {
            return productRepository.searchActive(search.trim());
        }
        return productRepository.findByActiveTrue();
    }

    public List<Product> findLowStock(int threshold) {
        return productRepository.findByActiveTrueAndStockQuantityLessThan(threshold);
    }

    public Product findById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    public Product create(Product product) {
        validateMinimumPrice(product);
        product.setId(null);
        return productRepository.save(product);
    }

    public Product update(UUID id, Product request) {
        validateMinimumPrice(request);
        Product existing = findById(id);
        existing.setName(request.getName());
        existing.setSku(request.getSku());
        existing.setDescription(request.getDescription());
        existing.setSize(request.getSize());
        existing.setColor(request.getColor());
        existing.setPrice(request.getPrice());
        existing.setCost(request.getCost());
        existing.setStockQuantity(request.getStockQuantity());
        existing.setActive(request.getActive());
        existing.setCategory(request.getCategory());
        return productRepository.save(existing);
    }

    private void validateMinimumPrice(Product product) {
        BigDecimal cost = product.getCost() == null ? BigDecimal.ZERO : product.getCost();
        BigDecimal minimumPrice = cost.multiply(new BigDecimal("1.15")).setScale(2, RoundingMode.CEILING);
        if (product.getPrice() == null || product.getPrice().compareTo(minimumPrice) < 0) {
            throw new IllegalArgumentException("Price must be at least Rs. " + minimumPrice + " (cost plus 15% profit)");
        }
    }

    public void delete(UUID id) {
        Product product = findById(id);
        product.setActive(false);
        productRepository.save(product);
    }
}
