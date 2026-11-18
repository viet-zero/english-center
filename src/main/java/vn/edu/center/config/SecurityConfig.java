package vn.edu.center.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, vn.edu.center.service.AccountDetailsService accounts) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/payments/payos/webhook"));
        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico",
                        "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/auth/password").authenticated()
                .requestMatchers("/api/admin/accounts", "/api/admin/accounts/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/users/students").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/users/employees").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/payments/payos/webhook").permitAll()
                .requestMatchers("/api/payments/payos/reviews", "/api/payments/payos/reviews/**").hasRole("ACCOUNTANT")
                .requestMatchers("/api/payments/student-refunds", "/api/payments/student-refunds/**").hasRole("ACCOUNTANT")
                .requestMatchers(HttpMethod.POST, "/api/academics/student/requests").hasRole("STUDENT")
                .requestMatchers("/api/enrollments", "/api/enrollments/**").hasRole("STUDENT")
                .requestMatchers(HttpMethod.POST, "/api/invoices/*/payments").hasRole("ACCOUNTANT")
                .requestMatchers(HttpMethod.GET, "/api/invoices", "/api/invoices/**").hasRole("STUDENT")
                .requestMatchers(HttpMethod.GET, "/api/catalog/**").hasRole("STUDENT")
                .requestMatchers("/api/training/**").hasAnyRole("TRAINING_STAFF", "ADMIN")
                .requestMatchers("/api/academics/teacher/**").hasRole("TEACHER")
                .requestMatchers(HttpMethod.PUT, "/api/academics/student/classes/*/assignments/*/submission").hasRole("STUDENT")
                .requestMatchers(HttpMethod.GET, "/api/academics/student/**").hasRole("STUDENT")
                .requestMatchers(HttpMethod.PUT, "/api/academics/student/notifications/*/read").hasRole("STUDENT")
                .anyRequest().denyAll());
        http.addFilterBefore(new AccountSessionFilter(accounts),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        http.requestCache(cache -> cache.disable());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                .accessDeniedHandler((request, response, exception) ->
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN)));
        http.formLogin(login -> login
                .loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) ->
                        response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                .failureHandler((request, response, exception) ->
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)));
        http.logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) ->
                        response.setStatus(HttpServletResponse.SC_NO_CONTENT)));
        return http.build();
    }
}
