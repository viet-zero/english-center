package vn.edu.center.controller;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @GetMapping("/me")
    public SessionUser me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.substring("ROLE_".length()))
                .sorted()
                .toList();
        return new SessionUser(authentication.getName(), roles);
    }

    public record CsrfResponse(String headerName, String token) {}
    public record SessionUser(String username, List<String> roles) {}
}
