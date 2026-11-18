package vn.edu.center.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SePayWebhookService {
    private static final Pattern PAYMENT_CODE=Pattern.compile("(?i)(?<![A-Z0-9])EC[1-9][0-9]{0,15}(?![A-Z0-9])");
    private final JdbcTemplate sql;
    private final EnrollmentService enrollments;
    private final SePayClient sepay;

    public SePayWebhookService(JdbcTemplate sql,EnrollmentService enrollments,SePayClient sepay) {
        this.sql=sql;
        this.enrollments=enrollments;
        this.sepay=sepay;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void accept(Event event) {
        long amount;
        try { amount=event.transferAmount().longValueExact(); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException("SePay yêu cầu số tiền nguyên VND."); }
        String code=paymentCode(event.code(),event.content());
        Long orderCode=code==null ? null : Long.valueOf(code.substring(2));
        var orders=orderCode==null ? List.<Map<String,Object>>of() :
                sql.queryForList("SELECT * FROM don_thanh_toan WHERE order_code=? AND provider='SEPAY'",orderCode);
        String expected=orders.isEmpty() ? sepay.accountNumber() : (String)orders.getFirst().get("receiving_account");
        if (expected==null || expected.isBlank() || !expected.equals(event.accountNumber())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Sai tài khoản nhận.");
        }
        String reference=sepay.reference(event.id());
        String fingerprint=fingerprint(event,amount,code);
        sql.update("""
                INSERT INTO sepay_su_kien(reference,transaction_id,fingerprint,receiving_account,transfer_type,
                    amount,payment_code,content,bank_reference) VALUES (?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE reference=reference
                """,reference,event.id(),fingerprint,event.accountNumber(),event.transferType(),amount,
                code,event.content(),event.referenceCode());
        var existing=sql.queryForMap("SELECT fingerprint,status FROM sepay_su_kien WHERE reference=? FOR UPDATE",reference);
        if (!fingerprint.equals(existing.get("fingerprint"))) {
            throw new IllegalStateException("ID giao dịch SePay đã được dùng với dữ liệu khác.");
        }
        if (!"RECEIVED".equals(existing.get("status"))) return;
        if (!"in".equals(event.transferType())) {
            sql.update("UPDATE sepay_su_kien SET status='IGNORED' WHERE reference=?",reference);
            return;
        }
        if (orders.isEmpty()) {
            sql.update("UPDATE sepay_su_kien SET status='UNMATCHED' WHERE reference=?",reference);
            return;
        }
        var order=orders.getFirst();
        enrollments.settle(orderCode,(String)order.get("link_id"),reference,amount);
        sql.update("UPDATE sepay_su_kien SET status='PROCESSED' WHERE reference=?",reference);
    }

    public List<Map<String,Object>> unmatched() {
        return sql.queryForList("""
                SELECT transaction_id AS transactionId,amount,content,bank_reference AS bankReference,received_at AS receivedAt
                FROM sepay_su_kien WHERE status='UNMATCHED' ORDER BY received_at DESC,transaction_id DESC
                """);
    }

    private String paymentCode(String supplied,String content) {
        Set<String> matches=new HashSet<>();
        var matcher=PAYMENT_CODE.matcher(content==null ? "" : content);
        while (matcher.find()) matches.add(matcher.group().toUpperCase(Locale.ROOT));
        if (supplied!=null && !supplied.isBlank()) {
            String normalized=supplied.toUpperCase(Locale.ROOT);
            if (!normalized.matches("EC[1-9][0-9]{0,15}")) return null;
            matches.add(normalized);
        }
        return matches.size()==1 ? matches.iterator().next() : null;
    }

    private String fingerprint(Event event,long amount,String code) {
        try {
            String value=String.join("|",event.id().toString(),event.accountNumber(),event.transferType(),
                    Long.toString(amount),String.valueOf(code),String.valueOf(event.content()),String.valueOf(event.referenceCode()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record Event(@NotNull @Positive Long id,
            @NotBlank @Size(max=50) String accountNumber,
            @NotBlank @jakarta.validation.constraints.Pattern(regexp="in|out") String transferType,
            @NotNull @DecimalMin("1") @DecimalMax("9999999999999") BigDecimal transferAmount,
            @Size(max=50) String code,@Size(max=2000) String content,@Size(max=100) String referenceCode) {}
}
