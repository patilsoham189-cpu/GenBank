package com.genbank.apnabank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/signup", "/send-otp", "/user/login", "/admin/login").permitAll()
                .requestMatchers("/user/**").permitAll()
                .requestMatchers("/admin/**").permitAll()
                .requestMatchers("/logout").permitAll()
                .anyRequest().permitAll()
            )
            // Enable CSRF protection for all state-changing endpoints (Thymeleaf th:action auto-injects tokens)
            // Exempt /send-otp for seamless AJAX fetch integration
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/send-otp")
            )
            // Disable default Spring Security login form — we use our own branded forms.
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            // Session fixation protection
            .sessionManagement(session -> session
                .sessionFixation().migrateSession()
            )
            // Configure logout to redirect to /?logout and invalidate session
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }
}
