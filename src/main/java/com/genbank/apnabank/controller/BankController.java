package com.genbank.apnabank.controller;

import com.genbank.apnabank.dto.TransferRequest;
import com.genbank.apnabank.model.AccountStatus;
import com.genbank.apnabank.model.Admin;
import com.genbank.apnabank.model.Transaction;
import com.genbank.apnabank.model.User;
import com.genbank.apnabank.service.BankingService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
public class BankController {

    private final BankingService bankingService;

    public BankController(BankingService bankingService) {
        this.bankingService = bankingService;
    }

    @GetMapping("/")
    public String showIndexPage() {
        return "index";
    }

    /**
     * AJAX endpoint to dispatch OTP with duplicate check and offline error handling.
     */
    @PostMapping("/send-otp")
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleOtpRequest(@RequestParam String email) {
        Map<String, String> response = new HashMap<>();
        try {
            bankingService.sendOtp(email);
            response.put("status", "success");
            response.put("message", "Verification code dispatched! Please check your server terminal console to view the 6-digit OTP code.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to dispatch OTP: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Handles customer registration with duplicate check error retention.
     */
    @PostMapping("/signup")
    public String registerCustomer(@ModelAttribute @Valid User user, BindingResult bindingResult,
                                   @RequestParam String otp, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("error", "Please correct the form errors and try again.");
            model.addAttribute("activeTab", "signup");
            return "index";
        }
        boolean isOtpValid = bankingService.verifyOtp(user.getEmail(), otp);
        if (!isOtpValid) {
            model.addAttribute("error", "Invalid or Expired OTP! Registration Failed.");
            model.addAttribute("activeTab", "signup");
            return "index";
        }
        try {
            User registered = bankingService.registerUser(user);
            model.addAttribute("message", "Account Created Successfully! Account No: " + registered.getAccountNumber());
            model.addAttribute("activeTab", "user-login");
            return "index";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("activeTab", "signup");
            return "index";
        }
    }

    @PostMapping("/user/login")
    public String userLogin(@RequestParam String email, @RequestParam String password, HttpSession session, Model model) {
        Optional<User> user = bankingService.loginUser(email, password);
        if (user.isPresent()) {
            session.setAttribute("currentUser", user.get());
            return "redirect:/user/dashboard";
        }
        model.addAttribute("error", "Invalid User Credentials.");
        model.addAttribute("activeTab", "user-login");
        return "index";
    }

    @PostMapping("/admin/login")
    public String adminLogin(@RequestParam String email, @RequestParam String password, HttpSession session, Model model) {
        Optional<Admin> admin = bankingService.loginAdmin(email, password);
        if (admin.isPresent()) {
            session.setAttribute("adminUser", admin.get());
            return "redirect:/admin/dashboard";
        }
        model.addAttribute("error", "Invalid Admin Credentials.");
        model.addAttribute("activeTab", "admin-login");
        return "index";
    }

    /**
     * Retail customer dashboard — loads fresh account profile and live transaction passbook.
     */
    @GetMapping("/user/dashboard")
    public String showUserDashboard(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("currentUser");
        if (sessionUser == null) return "redirect:/";

        Optional<User> freshUser = bankingService.getUserById(sessionUser.getId());
        if (freshUser.isEmpty()) {
            session.invalidate();
            return "redirect:/";
        }

        User user = freshUser.get();
        session.setAttribute("currentUser", user);
        model.addAttribute("user", user);

        // Fetch live customer passbook ledger
        List<Transaction> transactions = bankingService.getTransactionsForAccount(user.getAccountNumber());
        model.addAttribute("transactions", transactions);

        return "user_dashboard";
    }

    /**
     * CBS Admin terminal — live metrics, customer search filter, and recent 50 clearing entries.
     */
    @GetMapping("/admin/dashboard")
    public String showAdminDashboard(@RequestParam(required = false) String search,
                                     HttpSession session, Model model) {
        Admin admin = (Admin) session.getAttribute("adminUser");
        if (admin == null) return "redirect:/";
        model.addAttribute("admin", admin);

        List<User> customers = bankingService.searchUsers(search);
        model.addAttribute("customers", customers);
        model.addAttribute("customerCount", bankingService.getAllUsers().size());
        model.addAttribute("totalDeposits", bankingService.getTotalDeposits());
        model.addAttribute("transactions", bankingService.getRecentBankTransactions());
        model.addAttribute("searchQuery", search != null ? search : "");
        return "admin_dashboard";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/?logout";
    }

    /**
     * Handles fund transfer from the logged-in customer to a beneficiary.
     */
    @PostMapping("/user/transfer")
    public String transferMoney(@Valid @ModelAttribute TransferRequest transferRequest,
                                BindingResult bindingResult,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        User sessionUser = (User) session.getAttribute("currentUser");
        if (sessionUser == null) return "redirect:/";

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error",
                    bindingResult.getFieldErrors().get(0).getDefaultMessage());
            return "redirect:/user/dashboard";
        }

        String result = bankingService.transferMoney(
                sessionUser.getId(),
                transferRequest.getBeneficiaryAccount(),
                transferRequest.getIfscCode(),
                transferRequest.getAmount()
        );

        if (result.startsWith("SUCCESS")) {
            redirectAttributes.addFlashAttribute("message", result);
        } else {
            redirectAttributes.addFlashAttribute("error", result);
        }
        return "redirect:/user/dashboard";
    }

    /**
     * Admin CBS action: freeze a customer account.
     */
    @PostMapping("/admin/customer/freeze")
    public String freezeCustomer(@RequestParam Long userId, HttpSession session, RedirectAttributes redirectAttributes) {
        Admin admin = (Admin) session.getAttribute("adminUser");
        if (admin == null) return "redirect:/";

        bankingService.updateAccountStatus(userId, AccountStatus.FROZEN);
        redirectAttributes.addFlashAttribute("message", "Customer account (ID: " + userId + ") is now FROZEN.");
        return "redirect:/admin/dashboard";
    }

    /**
     * Admin CBS action: unfreeze / restore a customer account to ACTIVE.
     */
    @PostMapping("/admin/customer/unfreeze")
    public String unfreezeCustomer(@RequestParam Long userId, HttpSession session, RedirectAttributes redirectAttributes) {
        Admin admin = (Admin) session.getAttribute("adminUser");
        if (admin == null) return "redirect:/";

        bankingService.updateAccountStatus(userId, AccountStatus.ACTIVE);
        redirectAttributes.addFlashAttribute("message", "Customer account (ID: " + userId + ") has been restored to ACTIVE.");
        return "redirect:/admin/dashboard";
    }
}
