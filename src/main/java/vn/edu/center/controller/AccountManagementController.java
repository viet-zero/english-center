package vn.edu.center.controller;

import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.center.service.AccountManagementService;

@RestController
public class AccountManagementController {
    private final AccountManagementService accounts;

    public AccountManagementController(AccountManagementService accounts) { this.accounts = accounts; }

    @GetMapping("/api/admin/accounts")
    public List<Map<String, Object>> list() { return accounts.list(); }

    @PutMapping("/api/admin/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable String id, @Valid @RequestBody AccountManagementService.Update request) {
        accounts.update(id, request);
    }

    @PutMapping("/api/auth/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(Principal principal, @Valid @RequestBody AccountManagementService.PasswordChange request,
            HttpServletRequest http) {
        accounts.changePassword(principal.getName(), request);
        var session = http.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }
}
