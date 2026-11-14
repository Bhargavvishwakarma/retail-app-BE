package com.retail.service;

import com.retail.config.CacheNames;
import com.retail.model.Category;
import com.retail.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    @CacheEvict(cacheNames = CacheNames.DASHBOARD_ANALYTICS, allEntries = true)
    public Category create(Category category) {
        return categoryRepository.save(category);
    }

    @CacheEvict(cacheNames = CacheNames.DASHBOARD_ANALYTICS, allEntries = true)
    public Category update(UUID id, Category request) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        return categoryRepository.save(existing);
    }

    @CacheEvict(cacheNames = CacheNames.DASHBOARD_ANALYTICS, allEntries = true)
    public void delete(UUID id) {
        categoryRepository.deleteById(id);
    }
}
