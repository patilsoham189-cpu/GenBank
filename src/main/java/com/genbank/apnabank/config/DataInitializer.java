package com.genbank.apnabank.config;

import com.genbank.apnabank.model.Admin;
import com.genbank.apnabank.model.Transaction;
import com.genbank.apnabank.model.TransactionType;
import com.genbank.apnabank.model.User;
import com.genbank.apnabank.repository.AdminRepository;
import com.genbank.apnabank.repository.TransactionRepository;
import com.genbank.apnabank.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AdminRepository adminRepository,
                           UserRepository userRepository,
                           TransactionRepository transactionRepository,
                           PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Upgrade any legacy plain text admin passwords to BCrypt
        adminRepository.findAll().forEach(admin -> {
            if (admin.getPassword() != null && !admin.getPassword().startsWith("$2")) {
                admin.setPassword(passwordEncoder.encode(admin.getPassword()));
                adminRepository.save(admin);
            }
        });

        // Ensure primary admin account exists
        Optional<Admin> admin1Opt = adminRepository.findByUsernameIgnoreCase("admin1");
        Optional<Admin> adminOpt = adminRepository.findByUsernameIgnoreCase("admin");
        if (adminOpt.isEmpty() && admin1Opt.isPresent()) {
            Admin a1 = admin1Opt.get();
            a1.setUsername("admin");
            adminRepository.save(a1);
        } else if (adminOpt.isEmpty()) {
            Admin admin = new Admin();
            admin.setUsername("admin");
            admin.setEmail("admin@genbank.com");
            admin.setPassword(passwordEncoder.encode("Admin@123"));
            adminRepository.save(admin);
            System.out.println("[DataInitializer] Initialized administrator account.");
        }

        // Clean up & standardize user records
        List<User> users = userRepository.findAll();
        for (User user : users) {
            boolean modified = false;

            // Upgrade legacy plain text passwords
            if (user.getPassword() != null && !user.getPassword().startsWith("$2")) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
                modified = true;
            }

            // Standardize IFSC code if missing or legacy
            if (user.getIfscCode() == null || "GB0001234".equalsIgnoreCase(user.getIfscCode())) {
                user.setIfscCode(BankConstants.BANK_IFSC);
                modified = true;
            }

            if (modified) {
                userRepository.save(user);
            }

            // Ensure ledger integrity: if a customer has zero transactions, record an opening balance entry
            List<Transaction> userTxs = transactionRepository.findByAccountNumberOrderByCreatedAtDesc(user.getAccountNumber());
            if (userTxs.isEmpty() && user.getBalance() != null && user.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                Transaction openingTx = new Transaction(
                        user.getId(),
                        "REW" + System.currentTimeMillis() + user.getId(),
                        user.getAccountNumber(),
                        "SYSTEM",
                        "GenBank Digital Onboarding",
                        TransactionType.CREDIT,
                        user.getBalance(),
                        user.getBalance(),
                        "Initial Digital Account Activation Reward"
                );
                transactionRepository.save(openingTx);
                System.out.println("[DataInitializer] Reconciled opening ledger entry for account " + user.getAccountNumber());
            }
        }
    }
}
