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
        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .disabled(!"ACTIVE".equals(account.getStatus()))
                .roles(accounts.findRoleIds(account.getId()).toArray(String[]::new))
                .build();
    }
}
