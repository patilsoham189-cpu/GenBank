package com.genbank.apnabank.repository;

import com.genbank.apnabank.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByAccountNumber(String accountNumber);
    Optional<User> findByAccountNumberAndIfscCode(String accountNumber, String ifscCode);
    boolean existsByAccountNumber(String accountNumber);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    List<User> findByFullNameContainingIgnoreCaseOrAccountNumberContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name, String accountNumber, String email);
}
