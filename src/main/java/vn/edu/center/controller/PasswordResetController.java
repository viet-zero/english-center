package vn.edu.center.controller;

import java.security.Principal;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.edu.center.service.PasswordResetService;

@RestController
public class PasswordResetController {
    private final PasswordResetService resets;
    public PasswordResetController(PasswordResetService resets) { this.resets=resets; }

    @PostMapping("/api/auth/password/forgot")
    public Map<String,String> forgot(@Valid @RequestBody Forgot body) {
        resets.forgot(body.email());
        return Map.of("message",PasswordResetService.MESSAGE);
    }

    @PostMapping("/api/auth/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(Principal principal,HttpServletRequest http,@Valid @RequestBody Reset body) {
        String username=resets.reset(body.token(),body.newPassword(),body.confirmPassword());
        if (principal!=null && username.equals(principal.getName())) {
            var session=http.getSession(false);
            if (session!=null) session.invalidate();
            SecurityContextHolder.clearContext();
        }
    }

    @GetMapping("/api/admin/password-reset-emails")
    public List<Map<String,Object>> outbox() { return resets.outbox(); }

    public record Forgot(@NotBlank @Email @Size(max=254) String email) {}
    public record Reset(@NotBlank @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_-]{43}") String token,
            @NotBlank @Size(min=8,max=72) String newPassword,
            @NotBlank @Size(max=72) String confirmPassword) {
        @Override public String toString() { return "Reset[redacted]"; }
    }
}
