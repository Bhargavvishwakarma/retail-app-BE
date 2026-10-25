package com.retail.controller;

import com.retail.model.Customer;
import com.retail.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public List<Customer> all(@RequestParam(required = false) String search) {
        return customerService.findAll(search);
    }

    @GetMapping("/{id}")
    public Customer get(@PathVariable UUID id) {
        return customerService.findById(id);
    }

    @PostMapping
    public Customer create(@Valid @RequestBody Customer customer) {
        return customerService.create(customer);
    }

    @PutMapping("/{id}")
    public Customer update(@PathVariable UUID id, @Valid @RequestBody Customer request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        customerService.delete(id);
    }
}
