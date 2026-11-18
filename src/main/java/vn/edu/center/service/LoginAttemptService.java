package vn.edu.center.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAttemptService {
    private final JdbcTemplate sql;
    public LoginAttemptService(JdbcTemplate sql) { this.sql=sql; }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void failed(String username,Exception failure) {
        if (!(failure instanceof BadCredentialsException) || username==null || username.length()>100) return;
        var rows=sql.queryForList("SELECT id,status,failed_login_count FROM tai_khoan WHERE username=? FOR UPDATE",username);
        if (rows.isEmpty() || !"ACTIVE".equals(rows.getFirst().get("status"))) return;
        var account=rows.getFirst();
        int attempts=((Number)account.get("failed_login_count")).intValue()+1;
        if (attempts>=5) {
            sql.update("""
                    UPDATE tai_khoan SET failed_login_count=5,status='LOCKED',lock_reason='FAILED_ATTEMPTS',
                      auth_version=auth_version+1 WHERE id=?
                    """,account.get("id"));
        } else {
            sql.update("UPDATE tai_khoan SET failed_login_count=? WHERE id=?",attempts,account.get("id"));
        }
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public boolean succeeded(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AccountDetailsService.SessionAccount previous)) return false;
        var rows=sql.queryForList("SELECT id,status,auth_version FROM tai_khoan WHERE username=? FOR UPDATE",authentication.getName());
        if (rows.isEmpty()) return false;
        var account=rows.getFirst();
        if (!"ACTIVE".equals(account.get("status")) || ((Number)account.get("auth_version")).longValue()!=previous.version()) return false;
        sql.update("UPDATE tai_khoan SET failed_login_count=0,last_login=CURRENT_TIMESTAMP WHERE id=?",account.get("id"));
        return true;
    }
}
