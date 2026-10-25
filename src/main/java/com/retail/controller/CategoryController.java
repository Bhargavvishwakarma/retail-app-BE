package com.retail.controller;

import com.retail.model.Category;
import com.retail.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<Category> all() {
        return categoryService.findAll();
    }

    @PostMapping
    public Category create(@Valid @RequestBody Category category) {
        return categoryService.create(category);
    }

    @PutMapping("/{id}")
    public Category update(@PathVariable UUID id, @Valid @RequestBody Category request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        categoryService.delete(id);
    }
}
