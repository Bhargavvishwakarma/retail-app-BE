package com.retail.service;

import com.retail.model.Customer;
import com.retail.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public List<Customer> findAll(String search) {
        if (search != null && !search.isBlank()) {
            return customerRepository.findByNameContainingIgnoreCaseOrPhoneContaining(search, search);
        }
        return customerRepository.findAll();
    }

    public Page<Customer> findPage(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        if (search != null && !search.isBlank()) {
            String query = search.trim();
            return customerRepository.findByNameContainingIgnoreCaseOrPhoneContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    query, query, query, pageable);
        }
        return customerRepository.findAll(pageable);
    }

    public Customer findById(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
    }

    public Customer create(Customer customer) {
        customer.setId(null);
        return customerRepository.save(customer);
    }

    public Customer update(UUID id, Customer request) {
        Customer existing = findById(id);
        existing.setName(request.getName());
        existing.setPhone(request.getPhone());
        existing.setEmail(request.getEmail());
        existing.setAddress(request.getAddress());
        return customerRepository.save(existing);
    }

    public void delete(UUID id) {
        customerRepository.deleteById(id);
    }
}
