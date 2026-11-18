package vn.edu.center.service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountManagementService {
    private final JdbcTemplate sql;
    private final PasswordEncoder passwords;
    private static final Set<String> EMPLOYEE_ROLES = Set.of("ADMIN", "TEACHER", "TRAINING_STAFF", "ACCOUNTANT");

    public AccountManagementService(JdbcTemplate sql, PasswordEncoder passwords) {
        this.sql = sql;
        this.passwords = passwords;
    }

    public List<Map<String, Object>> list() {
        return sql.queryForList("""
                SELECT a.id, a.username, a.status, a.auth_version AS version,a.failed_login_count AS failedLoginCount,a.lock_reason AS lockReason,
                       u.full_name AS fullName, u.phone, u.email, u.identity_number AS identityNumber,
                       (SELECT GROUP_CONCAT(r.role_id ORDER BY r.role_id)
                        FROM tai_khoan_vai_tro r WHERE r.account_id = a.id) AS roles,
                       EXISTS(SELECT 1 FROM hoc_vien s WHERE s.user_id = a.user_id) AS student,
                       EXISTS(SELECT 1 FROM nhan_vien e WHERE e.user_id = a.user_id
                         AND e.status = 'ACTIVE' AND e.deleted = false) AS employee
                FROM tai_khoan a JOIN nguoi_dung u ON u.id = a.user_id ORDER BY a.username
                """);
    }

    @Transactional
    public void update(String id, Update request) {
        // Serialize admin membership changes so concurrent edits cannot remove the last administrator.
        sql.queryForList("SELECT id FROM vai_tro WHERE id = 'ADMIN' FOR UPDATE");
        var account = sql.queryForList("SELECT * FROM tai_khoan WHERE id = ? FOR UPDATE", id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (((Number) account.get("auth_version")).longValue() != request.version()) {
            throw new IllegalStateException("Tài khoản đã thay đổi. Hãy tải lại danh sách.");
        }
        String userId = (String) account.get("user_id");
        boolean student = sql.queryForObject("SELECT COUNT(*) FROM hoc_vien WHERE user_id = ?", Long.class, userId) > 0;
        boolean employee = sql.queryForObject("""
                SELECT COUNT(*) FROM nhan_vien WHERE user_id = ? AND status = 'ACTIVE' AND deleted = false
                """, Long.class, userId) > 0;
        for (String role : request.roles()) {
            if ("STUDENT".equals(role) ? !student : !EMPLOYEE_ROLES.contains(role) || !employee) {
                throw new IllegalArgumentException("Vai trò không phù hợp hồ sơ học viên hoặc nhân viên.");
            }
        }
        boolean wasAdmin = sql.queryForObject(
                "SELECT COUNT(*) FROM tai_khoan_vai_tro WHERE account_id = ? AND role_id = 'ADMIN'",
                Long.class, id) > 0 && "ACTIVE".equals(account.get("status"));
        if (wasAdmin && (!"ACTIVE".equals(request.status()) || !request.roles().contains("ADMIN"))) {
            long count = sql.queryForObject("""
                    SELECT COUNT(*) FROM tai_khoan a JOIN tai_khoan_vai_tro r ON r.account_id = a.id
                    WHERE a.status = 'ACTIVE' AND r.role_id = 'ADMIN'
                    """, Long.class);
            if (count <= 1) throw new IllegalStateException("Cần giữ ít nhất một quản trị viên đang hoạt động.");
        }
        sql.update("""
                UPDATE nguoi_dung SET full_name = ?, phone = ?, email = ?, identity_number = ? WHERE id = ?
                """, request.fullName().strip(), nullable(request.phone()), nullable(request.email()),
                nullable(request.identityNumber()), userId);
        sql.update("DELETE FROM tai_khoan_vai_tro WHERE account_id = ?", id);
        for (String role : request.roles()) {
            sql.update("INSERT INTO tai_khoan_vai_tro(account_id,role_id) VALUES (?, ?)", id, role);
        }
        sql.update("""
                UPDATE tai_khoan SET failed_login_count=IF(status='LOCKED' AND ?='ACTIVE',0,failed_login_count),
                    status=?,lock_reason=?,
                    auth_version=auth_version+1 WHERE id=?
                """,request.status(),request.status(),"LOCKED".equals(request.status()) ? "ADMIN" : null,id);
        sql.update("UPDATE password_reset SET used_at=CURRENT_TIMESTAMP WHERE account_id=? AND used_at IS NULL",id);
    }

    @Transactional
    public void changePassword(String username, PasswordChange request) {
        var account = sql.queryForMap("SELECT * FROM tai_khoan WHERE username = ? FOR UPDATE", username);
        String hash = (String) account.get("password_hash");
        if (!passwords.matches(request.currentPassword(), hash)) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
        }
        String encoded=encodeNewPassword(hash,request.newPassword(),request.confirmPassword());
        sql.update("""
                UPDATE tai_khoan SET password_hash=?,failed_login_count=0,auth_version=auth_version+1 WHERE username=?
                """,encoded,username);
        sql.update("UPDATE password_reset SET used_at=CURRENT_TIMESTAMP WHERE account_id=? AND used_at IS NULL",account.get("id"));
    }

    public String encodeNewPassword(String currentHash,String next,String confirm) {
        if (!next.equals(confirm)) throw new IllegalArgumentException("Xác nhận mật khẩu không khớp.");
        if (next.length()<8 || next.getBytes(StandardCharsets.UTF_8).length>72
                || !next.matches("(?s).*[A-Z].*") || !next.matches("(?s).*[a-z].*") || !next.matches("(?s).*[0-9].*")) {
            throw new IllegalArgumentException("Mật khẩu cần ít nhất 8 ký tự, chữ hoa, chữ thường, chữ số và tối đa 72 byte UTF-8.");
        }
        if (passwords.matches(next,currentHash)) throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        return passwords.encode(next);
    }
    private String nullable(String value) { return value == null || value.isBlank() ? null : value.strip(); }

    public record Update(
            @NotBlank @Size(max = 150) String fullName,
            @Pattern(regexp = "^$|0[0-9]{9}") String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 20) String identityNumber,
            @NotBlank @Pattern(regexp = "ACTIVE|LOCKED") String status,
            @NotEmpty Set<@NotBlank String> roles,
            @NotNull @PositiveOrZero Long version) {}

    public record PasswordChange(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword,
            @NotBlank @Size(max = 72) String confirmPassword) {
        @Override public String toString() { return "PasswordChange[redacted]"; }
    }
}
