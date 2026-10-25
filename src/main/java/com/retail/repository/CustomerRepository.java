package com.retail.repository;

import com.retail.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByNameContainingIgnoreCaseOrPhoneContaining(String name, String phone);
}
