package com.retail.repository;

import com.retail.model.AppUser;
import com.retail.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<AppUser> findByGoogleSub(String googleSub);
    boolean existsByRole(UserRole role);
    long countByRoleAndActiveTrue(UserRole role);


    List<AppUser> findAll(Sort sort);
    Page<AppUser> findAll(Pageable pageable);
}
