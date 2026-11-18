package vn.edu.center.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetService {
    public static final String MESSAGE="Nếu email đã đăng ký và tài khoản được phép khôi phục, bạn sẽ nhận được liên kết đặt lại mật khẩu.";
    private final JdbcTemplate sql;
    private final AccountManagementService accounts;
    private final boolean demo;
    private final String baseUrl;
    private final SecureRandom random=new SecureRandom();

    public PasswordResetService(JdbcTemplate sql,AccountManagementService accounts,
            @Value("${auth.email-demo:false}") boolean demo,
            @Value("${auth.reset-base-url:http://127.0.0.1:5175/}") String baseUrl) {
        this.sql=sql;
        this.accounts=accounts;
        this.demo=demo;
        var uri=java.net.URI.create(baseUrl);
        if (!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getQuery()!=null || uri.getFragment()!=null) throw new IllegalArgumentException("Invalid auth.reset-base-url");
        this.baseUrl=baseUrl;
    }

    public boolean demo() { return demo; }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void forgot(String email) {
        if (!demo) return;
        var ids=sql.queryForList("""
                SELECT a.id FROM tai_khoan a JOIN nguoi_dung u ON u.id=a.user_id WHERE u.email=?
                """,String.class,email.strip());
        if (ids.size()!=1) return;
        String id=ids.getFirst();
        var account=sql.queryForMap("SELECT user_id,status,lock_reason FROM tai_khoan WHERE id=? FOR UPDATE",id);
        var recipients=sql.queryForList("SELECT email FROM nguoi_dung WHERE id=? AND email=?",String.class,account.get("user_id"),email.strip());
        if (recipients.isEmpty() || ("LOCKED".equals(account.get("status")) && !"FAILED_ATTEMPTS".equals(account.get("lock_reason")))) return;
        byte[] bytes=new byte[32];
        random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sql.update("""
                INSERT INTO password_reset(account_id,token_hash,expires_at) VALUES (?,?,CURRENT_TIMESTAMP(6)+INTERVAL 5 MINUTE)
                ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash),created_at=CURRENT_TIMESTAMP(6),
                    expires_at=CURRENT_TIMESTAMP(6)+INTERVAL 5 MINUTE,used_at=NULL
                """,id,hash(token));
        String url=UriComponentsBuilder.fromUriString(baseUrl).queryParam("reset_token",token).build().encode().toUriString();
        sql.update("""
                INSERT INTO demo_email_outbox(id,account_id,recipient,subject,reset_url,token_hash,expires_at)
                VALUES (?,?,?,?,?,?,(SELECT expires_at FROM password_reset WHERE account_id=?))
                """,UUID.randomUUID().toString(),id,recipients.getFirst(),"Đặt lại mật khẩu English Center",url,hash(token),id);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public String reset(String token,String next,String confirm) {
        String digest=hash(token);
        var ids=sql.queryForList("SELECT account_id FROM password_reset WHERE token_hash=?",String.class,digest);
        if (ids.isEmpty()) throw invalid();
        String id=ids.getFirst();
        var account=sql.queryForMap("SELECT username,password_hash,status,lock_reason FROM tai_khoan WHERE id=? FOR UPDATE",id);
        var reset=sql.queryForMap("""
                SELECT token_hash,used_at,expires_at>CURRENT_TIMESTAMP(6) AS active FROM password_reset WHERE account_id=? FOR UPDATE
                """,id);
        Object active=reset.get("active");
        boolean unexpired=Boolean.TRUE.equals(active) || (active instanceof Number number && number.intValue()==1);
        if (!digest.equals(reset.get("token_hash")) || reset.get("used_at")!=null || !unexpired
                || ("LOCKED".equals(account.get("status")) && !"FAILED_ATTEMPTS".equals(account.get("lock_reason")))) throw invalid();
        String encoded=accounts.encodeNewPassword((String)account.get("password_hash"),next,confirm);
        sql.update("""
                UPDATE tai_khoan SET password_hash=?,status='ACTIVE',lock_reason=NULL,failed_login_count=0,
                    auth_version=auth_version+1 WHERE id=?
                """,encoded,id);
        sql.update("UPDATE password_reset SET used_at=CURRENT_TIMESTAMP WHERE account_id=?",id);
        return (String)account.get("username");
    }

    public List<Map<String,Object>> outbox() {
        if (!demo) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return sql.queryForList("""
                SELECT e.id,e.recipient,e.subject,e.reset_url AS resetUrl,e.created_at AS createdAt,e.expires_at AS expiresAt,
                       (r.token_hash=e.token_hash AND r.used_at IS NULL AND r.expires_at>CURRENT_TIMESTAMP(6)) AS valid
                FROM demo_email_outbox e LEFT JOIN password_reset r ON r.account_id=e.account_id
                ORDER BY e.created_at DESC,e.id DESC LIMIT 100
                """);
    }

    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private IllegalStateException invalid() {
        return new IllegalStateException("Liên kết không hợp lệ, đã được dùng hoặc đã hết hạn.");
    }
}
