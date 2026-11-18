package vn.edu.center.config;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.center.service.AccountDetailsService;
import vn.edu.center.service.AccountDetailsService.SessionAccount;

public class AccountSessionFilter extends OncePerRequestFilter {
    private final AccountDetailsService accounts;

    public AccountSessionFilter(AccountDetailsService accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && !(authentication.getPrincipal() instanceof SessionAccount)) {
            expire(request, response, chain);
            return;
        }
        if (authentication != null && authentication.getPrincipal() instanceof SessionAccount previous) {
            try {
                var current = (SessionAccount) accounts.loadUserByUsername(previous.getUsername());
                if (!current.isEnabled() || current.version() != previous.version()) {
                    expire(request, response, chain);
                    return;
                }
            } catch (UsernameNotFoundException missing) {
                expire(request, response, chain);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void expire(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        if (java.util.Set.of("/api/auth/csrf","/api/auth/password/forgot","/api/auth/password/reset").contains(request.getServletPath())) {
            chain.doFilter(request,response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
