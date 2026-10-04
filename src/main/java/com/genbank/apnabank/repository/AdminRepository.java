package com.genbank.apnabank.repository;

import com.genbank.apnabank.model.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUsername(String username);
    Optional<Admin> findByUsernameIgnoreCase(String username);
    Optional<Admin> findByEmailIgnoreCase(String email);
    Optional<Admin> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
}