package vn.edu.center.config;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import vn.edu.center.controller.AuthController;
import vn.edu.center.entity.Account;
import vn.edu.center.repository.AccountRepository;
import vn.edu.center.service.AccountDetailsService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(classes = {SecurityConfig.class, AccountDetailsService.class,
        AuthController.class, SecurityConfigTest.WebConfig.class})
class SecurityConfigTest {
    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @Autowired private PasswordEncoder passwords;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(accounts);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class))
                .build();
        stubAccount("student", "ACTIVE", "STUDENT");
        stubAccount("cashier", "ACTIVE", "ACCOUNTANT", "TEACHER");
        stubAccount("locked", "LOCKED", "STUDENT");
    }

    @Test
    void studentSessionCanReadInvoiceButCannotRecordPayment() throws Exception {
        var session = login("student", "demo-password", 204);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("student"))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"));
        mvc.perform(get("/api/invoices/INV01").session(session)).andExpect(status().isOk());
        var csrf = token(session);
        mvc.perform(post("/api/invoices/INV01/payments").session(csrf.session())
                .header(csrf.header(), csrf.value())).andExpect(status().isForbidden());
    }

    @Test
    void accountantNeedsCsrfAndCanLogOut() throws Exception {
        var session = login("cashier", "demo-password", 204);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.roles[0]").value("ACCOUNTANT"))
                .andExpect(jsonPath("$.roles[1]").value("TEACHER"));
        mvc.perform(post("/api/invoices/INV01/payments").session(session))
                .andExpect(status().isForbidden());
        var csrf = token(session);
        mvc.perform(post("/api/invoices/INV01/payments").session(csrf.session())
                .header(csrf.header(), csrf.value())).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(csrf.session())
                .header(csrf.header(), csrf.value())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
    }

    @Test
    void rejectsLockedAccountsAndIncorrectCredentials() throws Exception {
        login("locked", "demo-password", 401);
        login("student", "incorrect", 401);
        login("unknown", "demo-password", 401);
        mvc.perform(get("/api/invoices/INV01")).andExpect(status().isUnauthorized());
    }

    private void stubAccount(String username, String status, String... roles) {
        Account account = new Account();
        account.setId(username);
        account.setUsername(username);
        account.setStatus(status);
        account.setPasswordHash(passwords.encode("demo-password"));
        when(accounts.findByUsername(username)).thenReturn(Optional.of(account));
        when(accounts.findRoleIds(username)).thenReturn(List.of(roles));
    }

    private MockHttpSession login(String username, String password, int expected) throws Exception {
        var csrf = token(new MockHttpSession());
        var result = mvc.perform(post("/api/auth/login").session(csrf.session())
                .header(csrf.header(), csrf.value())
                .contentType("application/x-www-form-urlencoded")
                .param("username", username).param("password", password))
                .andExpect(status().is(expected)).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Csrf token(MockHttpSession session) throws Exception {
        var result = mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        var json = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        return new Csrf((MockHttpSession) result.getRequest().getSession(false),
                json.get("headerName").asText(), json.get("token").asText());
    }

    private record Csrf(MockHttpSession session, String header, String value) {}

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class WebConfig {
        @Bean
        AccountRepository accounts() {
            return mock(AccountRepository.class);
        }

        @Bean
        InvoiceEndpoints invoiceEndpoints() {
            return new InvoiceEndpoints();
        }
    }

    @RestController
    static class InvoiceEndpoints {
        @GetMapping("/api/invoices/{id}")
        String invoice() {
            return "invoice";
        }

        @PostMapping("/api/invoices/{id}/payments")
        String recordPayment() {
            return "payment";
        }
    }
}
