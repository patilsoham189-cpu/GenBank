package com.genbank.apnabank.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false)
	private String accountNumber;
	
	@Column(nullable = false, name = "ifsc_code")
	private String ifscCode;

	@NotBlank(message = "Full name is required")
	@Column(nullable = false)
	private String fullName;

	@NotBlank(message = "Email is required")
	@Email(message = "Invalid email format")
	@Column(unique = true, nullable = false)
	private String email;

	@NotBlank(message = "Phone number is required")
	@Pattern(regexp = "[6-9][0-9]{9}", message = "Invalid Indian mobile number")
	@Column(unique = true, nullable = false)
	private String phone;

	@NotBlank(message = "Password is required")
	@Column(nullable = false)
	private String password;

	@Column(nullable = false)
	private BigDecimal balance = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountStatus status = AccountStatus.ACTIVE;

	@Version
	private Long version;
}