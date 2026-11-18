package vn.edu.center.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.center.entity.Account;
import vn.edu.center.repository.AccountRepository;

@Service
public class AccountDetailsService implements UserDetailsService {
    private final AccountRepository accounts;

    public AccountDetailsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        Account account = accounts.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
        return new SessionAccount(account, accounts.findRoleIds(account.getId()));
    }

    public static class SessionAccount extends User {
        private final long version;

        public SessionAccount(Account account, java.util.List<String> roles) {
            super(account.getUsername(), account.getPasswordHash(), "ACTIVE".equals(account.getStatus()),
                    true, true, true, roles.stream().map(role ->
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)).toList());
            this.version = account.getAuthVersion();
        }

        public long version() { return version; }
    }
}