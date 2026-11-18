package vn.edu.center.service;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CouponService {
    private final JdbcTemplate sql;
    public CouponService(JdbcTemplate sql) { this.sql=sql; }

    public List<Map<String,Object>> list() {
        return sql.queryForList("""
                SELECT v.id,v.code,v.course_id AS courseId,k.name AS courseName,v.amount,v.starts_at AS startsAt,
                    v.expires_at AS expiresAt,v.max_uses AS maxUses,v.status,
                    (SELECT COUNT(*) FROM luot_dung_ma u WHERE u.coupon_id=v.id) AS usedCount
                FROM ma_giam_gia v JOIN khoa_hoc k ON k.id=v.course_id ORDER BY v.starts_at DESC,v.code
                """);
    }

    @Transactional
    public String create(String code,String course,int amount,LocalDateTime starts,LocalDateTime expires,int maximum,String status) {
        if (!expires.isAfter(starts)) throw new IllegalArgumentException("Hạn dùng phải sau ngày bắt đầu.");
        String id=UUID.randomUUID().toString();
        sql.update("INSERT INTO ma_giam_gia(id,code,course_id,amount,starts_at,expires_at,max_uses,status) VALUES (?,?,?,?,?,?,?,?)",
                id,code.strip().toUpperCase(Locale.ROOT),course,amount,starts,expires,maximum,status);
        return id;
    }

    @Transactional
    public void update(String id,LocalDateTime starts,LocalDateTime expires,int maximum,String status) {
        if (!expires.isAfter(starts)) throw new IllegalArgumentException("Hạn dùng phải sau ngày bắt đầu.");
        if (sql.queryForList("SELECT id FROM ma_giam_gia WHERE id=? FOR UPDATE",String.class,id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        long used=sql.queryForObject("SELECT COUNT(*) FROM luot_dung_ma WHERE coupon_id=?",Long.class,id);
        if (maximum<used) throw new IllegalStateException("Giới hạn lượt dùng phải từ số lượt đã dùng trở lên.");
        sql.update("UPDATE ma_giam_gia SET starts_at=?,expires_at=?,max_uses=?,status=? WHERE id=?",starts,expires,maximum,status,id);
    }

    // Called inside the enrollment transaction. Lock is held until registration and usage commit together.
    public Pricing price(String student,String course,int tuition,String code) {
        if (code==null || code.isBlank()) return new Pricing(tuition,0,null,null);
        var rows=sql.queryForList("""
                SELECT *,starts_at<=CURRENT_TIMESTAMP(6) AND expires_at>CURRENT_TIMESTAMP(6) AS valid
                FROM ma_giam_gia WHERE code=? FOR UPDATE
                """,code.strip().toUpperCase(Locale.ROOT));
        if (rows.isEmpty()) throw new IllegalStateException("Mã giảm giá không tồn tại.");
        var coupon=rows.getFirst();
        Object valid=coupon.get("valid");
        boolean current=Boolean.TRUE.equals(valid) || (valid instanceof Number number && number.intValue()==1);
        if (!"ACTIVE".equals(coupon.get("status")) || !current) throw new IllegalStateException("Mã giảm giá chưa có hiệu lực hoặc đã hết hạn.");
        if (!course.equals(coupon.get("course_id"))) throw new IllegalStateException("Mã giảm giá không áp dụng cho khóa học này.");
        String id=(String)coupon.get("id");
        if (sql.queryForObject("SELECT COUNT(*) FROM luot_dung_ma WHERE coupon_id=? AND student_id=?",Long.class,id,student)>0) {
            throw new IllegalStateException("Bạn đã sử dụng mã giảm giá này.");
        }
        if (sql.queryForObject("SELECT COUNT(*) FROM luot_dung_ma WHERE coupon_id=?",Long.class,id)
                >=((Number)coupon.get("max_uses")).longValue()) throw new IllegalStateException("Mã giảm giá đã hết lượt sử dụng.");
        int discount=((Number)coupon.get("amount")).intValue();
        if (discount>tuition) throw new IllegalStateException("Mức giảm vượt học phí lớp đã chọn.");
        return new Pricing(tuition,discount,id,(String)coupon.get("code"));
    }

    public void validateSaved(String student,String course,Pricing saved) {
        Pricing current=price(student,course,saved.tuition(),saved.code());
        if (!Objects.equals(current.couponId(),saved.couponId()) || current.discount()!=saved.discount()) {
            throw new IllegalStateException("Thông tin mã giảm giá đã thay đổi. Giao dịch cần đối soát.");
        }
    }

    public void consume(Pricing pricing,String student,long order,String invoice) {
        if (pricing.couponId()==null) return;
        sql.update("INSERT INTO luot_dung_ma(coupon_id,student_id,order_code,invoice_id) VALUES (?,?,?,?)",
                pricing.couponId(),student,order,invoice);
    }

    public record Pricing(int tuition,int discount,String couponId,String code) {
        public int due() { return tuition-discount; }
    }
}
