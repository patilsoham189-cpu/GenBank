package com.genbank.apnabank;

import com.genbank.apnabank.model.*;
import com.genbank.apnabank.repository.AdminRepository;
import com.genbank.apnabank.repository.TransactionRepository;
import com.genbank.apnabank.repository.UserRepository;
import com.genbank.apnabank.service.BankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankingServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RestTemplate restTemplate;

    private PasswordEncoder passwordEncoder;
    private BankingService bankingService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        bankingService = new BankingService(userRepository, adminRepository, transactionRepository, passwordEncoder, restTemplate);
    }

    // ---- Registration Tests ----

    @Test
    @DisplayName("registerUser should hash password, generate account number, set initial balance, and create welcome transaction")
    void registerUser_ShouldHashPasswordAndGenerateAccountNumber() {
        User user = new User();
        user.setFullName("Test User");
        user.setEmail("test@example.com");
        user.setPhone("9876543210");
        user.setPassword("plaintext123");
        user.setIfscCode("GENB0001234");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("9876543210")).thenReturn(false);
        when(userRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = bankingService.registerUser(user);

        assertNotNull(result.getAccountNumber());
        assertTrue(result.getAccountNumber().startsWith("GB"));
        assertEquals(new BigDecimal("1000.00"), result.getBalance());
        assertEquals(AccountStatus.ACTIVE, result.getStatus());
        assertNotEquals("plaintext123", result.getPassword());
        assertTrue(passwordEncoder.matches("plaintext123", result.getPassword()));

        verify(userRepository).save(user);
        // Verify welcome reward transaction is recorded in ledger
        verify(transactionRepository).save(argThat(tx ->
                tx.getType() == TransactionType.CREDIT &&
                tx.getAmount().compareTo(new BigDecimal("1000.00")) == 0 &&
                tx.getNarration().contains("Activation Reward")
        ));
    }

    @Test
    @DisplayName("registerUser should throw exception when email already exists")
    void registerUser_DuplicateEmail_ShouldThrowException() {
        User user = new User();
        user.setEmail("existing@example.com");
        user.setPhone("9876543210");

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> bankingService.registerUser(user));
        assertTrue(ex.getMessage().contains("already registered"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("registerUser should throw exception when phone already exists")
    void registerUser_DuplicatePhone_ShouldThrowException() {
        User user = new User();
        user.setEmail("new@example.com");
        user.setPhone("9876543210");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("9876543210")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> bankingService.registerUser(user));
        assertTrue(ex.getMessage().contains("already registered"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("registerUser should retry on duplicate account number")
    void registerUser_ShouldRetryOnDuplicateAccountNumber() {
        User user = new User();
        user.setFullName("Test User");
        user.setEmail("test@example.com");
        user.setPhone("9876543210");
        user.setPassword("pass");
        user.setIfscCode("GENB0001234");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone(anyString())).thenReturn(false);
        when(userRepository.existsByAccountNumber(anyString()))
                .thenReturn(true)
                .thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = bankingService.registerUser(user);

        assertNotNull(result.getAccountNumber());
        verify(userRepository, atLeast(2)).existsByAccountNumber(anyString());
    }

    // ---- Login Tests ----

    @Test
    @DisplayName("loginUser should succeed with correct credentials")
    void loginUser_CorrectCredentials_ShouldSucceed() {
        User user = new User();
        user.setEmail("user@test.com");
        user.setPassword(passwordEncoder.encode("secret"));

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        Optional<User> result = bankingService.loginUser("user@test.com", "secret");
        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("loginUser should fail with wrong password")
    void loginUser_WrongPassword_ShouldFail() {
        User user = new User();
        user.setEmail("user@test.com");
        user.setPassword(passwordEncoder.encode("secret"));

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        Optional<User> result = bankingService.loginUser("user@test.com", "wrongpassword");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("loginUser should fail when user not found")
    void loginUser_UserNotFound_ShouldFail() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        Optional<User> result = bankingService.loginUser("unknown@test.com", "any");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("loginUser should support legacy plain text passwords and upgrade to BCrypt")
    void loginUser_PlainTextPassword_ShouldAutoUpgrade() {
        User user = new User();
        user.setEmail("legacy@test.com");
        user.setPassword("oldplainpassword");

        when(userRepository.findByEmail("legacy@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<User> result = bankingService.loginUser("legacy@test.com", "oldplainpassword");
        assertTrue(result.isPresent());
        assertTrue(passwordEncoder.matches("oldplainpassword", user.getPassword()));
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("loginAdmin should succeed with correct email and password")
    void loginAdmin_CorrectCredentials_ShouldSucceed() {
        Admin admin = new Admin();
        admin.setEmail("admin@genbank.com");
        admin.setPassword(passwordEncoder.encode("Admin@123"));

        when(adminRepository.findByEmailIgnoreCase("admin@genbank.com")).thenReturn(Optional.of(admin));

        Optional<Admin> result = bankingService.loginAdmin("admin@genbank.com", "Admin@123");
        assertTrue(result.isPresent());
        assertEquals("admin@genbank.com", result.get().getEmail());
    }

    @Test
    @DisplayName("loginAdmin should fail with wrong password")
    void loginAdmin_WrongPassword_ShouldFail() {
        Admin admin = new Admin();
        admin.setEmail("admin@genbank.com");
        admin.setPassword(passwordEncoder.encode("Admin@123"));

        when(adminRepository.findByEmailIgnoreCase("admin@genbank.com")).thenReturn(Optional.of(admin));

        Optional<Admin> result = bankingService.loginAdmin("admin@genbank.com", "WrongPassword");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("loginAdmin should support legacy plain text passwords and upgrade to BCrypt")
    void loginAdmin_PlainTextPassword_ShouldAutoUpgrade() {
        Admin admin = new Admin();
        admin.setEmail("admin@genbank.com");
        admin.setPassword("plainAdminPass");

        when(adminRepository.findByEmailIgnoreCase("admin@genbank.com")).thenReturn(Optional.of(admin));
        when(adminRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Admin> result = bankingService.loginAdmin("admin@genbank.com", "plainAdminPass");
        assertTrue(result.isPresent());
        assertTrue(passwordEncoder.matches("plainAdminPass", admin.getPassword()));
        verify(adminRepository).save(admin);
    }

    // ---- Transfer Tests ----

    @Test
    @DisplayName("transferMoney should succeed and record both DEBIT and CREDIT ledger transactions")
    void transferMoney_ValidTransfer_ShouldSucceedAndRecordTransactions() {
        User sender = new User();
        sender.setId(1L);
        sender.setFullName("Sender Name");
        sender.setBalance(new BigDecimal("5000.00"));
        sender.setAccountNumber("GB1234567890");
        sender.setStatus(AccountStatus.ACTIVE);

        User beneficiary = new User();
        beneficiary.setId(2L);
        beneficiary.setFullName("Beneficiary Name");
        beneficiary.setBalance(new BigDecimal("1000.00"));
        beneficiary.setAccountNumber("GB9876543210");
        beneficiary.setIfscCode("GENB0001234");
        beneficiary.setStatus(AccountStatus.ACTIVE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findByAccountNumberAndIfscCode("GB9876543210", "GENB0001234"))
                .thenReturn(Optional.of(beneficiary));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String result = bankingService.transferMoney(1L, "GB9876543210", "GENB0001234", new BigDecimal("2000.00"));

        assertTrue(result.startsWith("SUCCESS"));
        assertEquals(new BigDecimal("3000.00"), sender.getBalance());
        assertEquals(new BigDecimal("3000.00"), beneficiary.getBalance());
        verify(userRepository, times(2)).save(any(User.class));

        // Verify dual transaction entries were saved (1 DEBIT, 1 CREDIT) with proper user IDs
        verify(transactionRepository).save(argThat(tx ->
                tx.getType() == TransactionType.DEBIT &&
                Long.valueOf(1L).equals(tx.getUserId()) &&
                tx.getAmount().compareTo(new BigDecimal("2000.00")) == 0
        ));
        verify(transactionRepository).save(argThat(tx ->
                tx.getType() == TransactionType.CREDIT &&
                Long.valueOf(2L).equals(tx.getUserId()) &&
                tx.getAmount().compareTo(new BigDecimal("2000.00")) == 0
        ));
    }

    @Test
    @DisplayName("transferMoney should reject transfer if sender account is FROZEN")
    void transferMoney_FrozenSender_ShouldFail() {
        User sender = new User();
        sender.setId(1L);
        sender.setBalance(new BigDecimal("5000.00"));
        sender.setStatus(AccountStatus.FROZEN);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));

        String result = bankingService.transferMoney(1L, "GB9876543210", "GENB0001234", new BigDecimal("500.00"));

        assertTrue(result.contains("FROZEN"));
        verify(userRepository, never()).save(any(User.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("transferMoney should reject transfer if beneficiary account is FROZEN")
    void transferMoney_FrozenBeneficiary_ShouldFail() {
        User sender = new User();
        sender.setId(1L);
        sender.setBalance(new BigDecimal("5000.00"));
        sender.setStatus(AccountStatus.ACTIVE);

        User beneficiary = new User();
        beneficiary.setId(2L);
        beneficiary.setStatus(AccountStatus.FROZEN);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findByAccountNumberAndIfscCode("GB9876543210", "GENB0001234"))
                .thenReturn(Optional.of(beneficiary));

        String result = bankingService.transferMoney(1L, "GB9876543210", "GENB0001234", new BigDecimal("500.00"));

        assertTrue(result.contains("FROZEN"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("transferMoney should fail with insufficient balance")
    void transferMoney_InsufficientBalance_ShouldFail() {
        User sender = new User();
        sender.setId(1L);
        sender.setBalance(new BigDecimal("100.00"));
        sender.setStatus(AccountStatus.ACTIVE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));

        String result = bankingService.transferMoney(1L, "GB9876543210", "GENB0001234", new BigDecimal("500.00"));

        assertTrue(result.contains("Insufficient funds"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("transferMoney should fail when beneficiary not found")
    void transferMoney_BeneficiaryNotFound_ShouldFail() {
        User sender = new User();
        sender.setId(1L);
        sender.setBalance(new BigDecimal("5000.00"));
        sender.setStatus(AccountStatus.ACTIVE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findByAccountNumberAndIfscCode("GB0000000000", "GENB0001234"))
                .thenReturn(Optional.empty());

        String result = bankingService.transferMoney(1L, "GB0000000000", "GENB0001234", new BigDecimal("100.00"));

        assertTrue(result.contains("Beneficiary account not found"));
    }

    @Test
    @DisplayName("transferMoney should prevent self-transfer")
    void transferMoney_SelfTransfer_ShouldFail() {
        User sender = new User();
        sender.setId(1L);
        sender.setBalance(new BigDecimal("5000.00"));
        sender.setAccountNumber("GB1234567890");
        sender.setIfscCode("GENB0001234");
        sender.setStatus(AccountStatus.ACTIVE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findByAccountNumberAndIfscCode("GB1234567890", "GENB0001234"))
                .thenReturn(Optional.of(sender));

        String result = bankingService.transferMoney(1L, "GB1234567890", "GENB0001234", new BigDecimal("100.00"));

        assertTrue(result.contains("Cannot transfer funds to your own account"));
    }

    // ---- Admin Status Update & Query Tests ----

    @Test
    @DisplayName("updateAccountStatus should change account status")
    void updateAccountStatus_ShouldChangeStatus() {
        User user = new User();
        user.setId(1L);
        user.setStatus(AccountStatus.ACTIVE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        bankingService.updateAccountStatus(1L, AccountStatus.FROZEN);

        assertEquals(AccountStatus.FROZEN, user.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("getTransactionsForAccount should query transactionRepository")
    void getTransactionsForAccount_ShouldReturnList() {
        when(transactionRepository.findByAccountNumberOrderByCreatedAtDesc("GB123"))
                .thenReturn(Collections.emptyList());

        List<Transaction> list = bankingService.getTransactionsForAccount("GB123");
        assertNotNull(list);
        verify(transactionRepository).findByAccountNumberOrderByCreatedAtDesc("GB123");
    }

    // ---- OTP Pipeline Tests ----

    @Test
    @DisplayName("sendOtp should return otp when microservice succeeds")
    void sendOtp_Success_ShouldReturnOtp() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        java.util.Map<String, String> mockResponse = new java.util.HashMap<>();
        mockResponse.put("otp", "654321");
        mockResponse.put("status", "success");
        when(restTemplate.postForObject(anyString(), any(), eq(java.util.Map.class)))
                .thenReturn(mockResponse);

        String otp = bankingService.sendOtp("new@example.com");

        assertEquals("654321", otp);
    }

    @Test
    @DisplayName("sendOtp should reject already registered email")
    void sendOtp_DuplicateEmail_ShouldThrowException() {
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bankingService.sendOtp("existing@example.com"));

        assertTrue(ex.getMessage().contains("already registered"));
    }

    @Test
    @DisplayName("verifyOtp should return true when microservice succeeds")
    void verifyOtp_Success_ShouldReturnTrue() {
        when(restTemplate.postForObject(anyString(), any(), eq(java.util.Map.class)))
                .thenReturn(Collections.singletonMap("status", "success"));

        boolean result = bankingService.verifyOtp("user@example.com", "123456");

        assertTrue(result);
    }
}
