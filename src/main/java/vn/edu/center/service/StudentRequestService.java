package vn.edu.center.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentRequestService {
    private final JdbcTemplate sql;
    private final NotificationService notifications;
    private static final String REQUESTS = """
            SELECT r.id, r.enrollment_id AS enrollmentId, r.kind, r.reason, r.status,
                   r.created_at AS createdAt, r.review_note AS reviewNote, r.reviewed_by AS reviewedBy,
                   r.reviewed_at AS reviewedAt, r.paid_amount AS paidAmount,
                   r.remaining_value AS remainingValue, r.total_lessons AS totalLessons,
                   r.elapsed_lessons AS elapsedLessons, e.student_id AS studentId, c.class_code AS classCode,
                   u.full_name AS fullName, CONCAT('HD', i.invoice_number) AS invoiceCode,
                   f.note AS refundNote, f.created_at AS refundedAt
            FROM yeu_cau_hoc_vien r JOIN dang_ky_hoc_phan e ON e.id = r.enrollment_id
            JOIN lop_hoc c ON c.id = e.class_id JOIN hoc_vien s ON s.id = e.student_id
            JOIN nguoi_dung u ON u.id = s.user_id
            JOIN chi_tiet_hoa_don d ON d.enrollment_id = e.id JOIN hoa_don i ON i.id = d.invoice_id
            LEFT JOIN hoan_tien_demo f ON f.request_id = r.id
            """;

    public StudentRequestService(JdbcTemplate sql, NotificationService notifications) {
        this.sql = sql;
        this.notifications = notifications;
    }

    public List<Map<String, Object>> studentRequests(String student) {
        return sql.queryForList(REQUESTS + " WHERE e.student_id = ? ORDER BY r.created_at DESC, r.id", student);
    }

    public List<Map<String, Object>> trainingRequests() {
        return sql.queryForList(REQUESTS + " ORDER BY (r.status = 'PENDING') DESC, r.created_at DESC, r.id");
    }

    public List<Map<String, Object>> refunds() {
        return sql.queryForList(REQUESTS + """
                 WHERE r.kind = 'CANCEL' AND r.status IN ('APPROVED', 'REFUNDED')
                 ORDER BY (r.status = 'APPROVED') DESC, r.created_at DESC, r.id
                """);
    }

    public List<Map<String, Object>> enrollments(String student) {
        return sql.queryForList("""
                SELECT e.id, c.class_code AS classCode, m.name AS moduleName,
                       i.total_paid AS paidAmount,
                       (SELECT COUNT(*) FROM lich_hoc l WHERE l.class_id = c.id AND l.status <> 'CANCELLED') AS totalLessons,
                       (SELECT COUNT(*) FROM lich_hoc l WHERE l.class_id = c.id AND l.status <> 'CANCELLED'
                         AND TIMESTAMP(l.lesson_date,l.end_time) <= CURRENT_TIMESTAMP) AS elapsedLessons
                FROM dang_ky_hoc_phan e JOIN lop_hoc c ON c.id = e.class_id
                JOIN hoc_phan m ON m.id = c.module_id
                JOIN chi_tiet_hoa_don d ON d.enrollment_id = e.id JOIN hoa_don i ON i.id = d.invoice_id
                WHERE e.student_id = ? AND e.status = 'CONFIRMED' AND i.status = 'PAID'
                ORDER BY c.class_code
                """, student);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String submit(String student, String enrollmentId, String kind, String reason) {
        var context = lockEnrollment(enrollmentId, student);
        eligibility(context);
        if (sql.queryForObject("""
                SELECT COUNT(*) FROM yeu_cau_hoc_vien WHERE enrollment_id = ? AND status = 'PENDING'
                """, Long.class, enrollmentId) > 0) throw new IllegalStateException("Lớp này đã có yêu cầu chờ duyệt.");
        String id = UUID.randomUUID().toString();
        sql.update("INSERT INTO yeu_cau_hoc_vien(id,enrollment_id,kind,reason) VALUES (?,?,?,?)",
                id, enrollmentId, kind, reason.strip());
        notifications.notifyStudent(student, "Đã tiếp nhận yêu cầu " + ("CANCEL".equals(kind) ? "hủy" : "bảo lưu")
                + " lớp " + context.classCode() + ".");
        return id;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void review(String id, String employee, boolean approved, String note) {
        var request = lockRequest(id);
        String desired = approved ? "APPROVED" : "REJECTED";
        if (!"PENDING".equals(request.get("status"))) {
            if (desired.equals(request.get("status")) || (approved && "REFUNDED".equals(request.get("status")))) return;
            throw new IllegalStateException("Yêu cầu đã được xử lý.");
        }
        var context = lockEnrollment((String) request.get("enrollment_id"), null);
        BigDecimal value = null;
        Progress progress = null;
        if (approved) {
            progress = eligibility(context);
            value = context.paid().multiply(BigDecimal.valueOf(progress.total() - progress.elapsed()))
                    .divide(BigDecimal.valueOf(progress.total()), 0, RoundingMode.DOWN);
            sql.update("UPDATE dang_ky_hoc_phan SET status = ? WHERE id = ?",
                    "CANCEL".equals(request.get("kind")) ? "CANCELLED" : "ON_HOLD", context.enrollmentId());
        }
        sql.update("""
                UPDATE yeu_cau_hoc_vien SET status=?, reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP,
                  review_note=?, paid_amount=?, remaining_value=?, total_lessons=?, elapsed_lessons=? WHERE id=?
                """, desired, employee, note.strip(), approved ? context.paid() : null, value,
                progress == null ? null : progress.total(), progress == null ? null : progress.elapsed(), id);
        notifications.notifyStudent(context.studentId(), "Yêu cầu lớp " + context.classCode() + (approved ? " đã được duyệt." : " đã bị từ chối.")
                + (approved && "CANCEL".equals(request.get("kind")) ? " Chờ kế toán xử lý hoàn tiền demo." : ""));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void refund(String id, String employee, String note) {
        var request = lockRequest(id);
        if (!"CANCEL".equals(request.get("kind"))) throw new IllegalStateException("Bảo lưu không phát sinh hoàn tiền.");
        if ("REFUNDED".equals(request.get("status"))) return;
        if (!"APPROVED".equals(request.get("status"))) throw new IllegalStateException("Chỉ hoàn tiền sau khi đào tạo duyệt hủy.");
        var enrollment = sql.queryForMap("SELECT * FROM dang_ky_hoc_phan WHERE id = ?", request.get("enrollment_id"));
        if (!"CANCELLED".equals(enrollment.get("status"))) throw new IllegalStateException("Đăng ký chưa được hủy.");
        String invoiceId = sql.queryForObject("SELECT invoice_id FROM chi_tiet_hoa_don WHERE enrollment_id = ?",
                String.class, request.get("enrollment_id"));
        var invoice = sql.queryForMap("SELECT * FROM hoa_don WHERE id = ? FOR UPDATE", invoiceId);
        BigDecimal refunded = sql.queryForObject("SELECT COALESCE(SUM(amount),0) FROM hoan_tien_demo WHERE invoice_id=?",
                BigDecimal.class, invoiceId);
        BigDecimal received = sql.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM giao_dich WHERE invoice_id=? AND status='SUCCESS'
                """, BigDecimal.class, invoiceId);
        BigDecimal amount = (BigDecimal) request.get("remaining_value");
        if (amount == null || amount.signum() < 0 || amount.compareTo(received.subtract(refunded)) > 0
                || amount.compareTo(((BigDecimal) invoice.get("total_paid")).subtract(refunded)) > 0) {
            throw new IllegalStateException("Khoản hoàn vượt số tiền thực thu còn lại.");
        }
        sql.update("""
                INSERT INTO hoan_tien_demo(id,request_id,invoice_id,amount,employee_id,note) VALUES (?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), id, invoiceId, amount, employee, note.strip());
        sql.update("UPDATE yeu_cau_hoc_vien SET status='REFUNDED' WHERE id=?", id);
        notifications.notifyStudent((String) enrollment.get("student_id"), "Đã ghi nhận hoàn tiền demo "
                + amount.toPlainString() + " ₫ cho yêu cầu " + id + ".");
    }

    private Map<String, Object> lockRequest(String id) {
        return sql.queryForList("SELECT * FROM yeu_cau_hoc_vien WHERE id=? FOR UPDATE", id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Context lockEnrollment(String id, String owner) {
        var initial = sql.queryForList("SELECT student_id,class_id FROM dang_ky_hoc_phan WHERE id=?", id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String studentId = (String) initial.get("student_id");
        String classId = (String) initial.get("class_id");
        if (owner != null && !owner.equals(studentId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        sql.queryForList("SELECT id FROM hoc_vien WHERE id=? FOR UPDATE", studentId);
        String classCode = sql.queryForObject("SELECT class_code FROM lop_hoc WHERE id=? FOR UPDATE", String.class, classId);
        var enrollment = sql.queryForMap("SELECT * FROM dang_ky_hoc_phan WHERE id=? FOR UPDATE", id);
        if (!"CONFIRMED".equals(enrollment.get("status"))) throw new IllegalStateException("Đăng ký không còn đang học.");
        String invoiceId = sql.queryForObject("SELECT invoice_id FROM chi_tiet_hoa_don WHERE enrollment_id=?",
                String.class, id);
        var invoice = sql.queryForMap("SELECT * FROM hoa_don WHERE id=? FOR UPDATE", invoiceId);
        if (!"PAID".equals(invoice.get("status"))) throw new IllegalStateException("Học phí chưa thanh toán đủ.");
        BigDecimal received = sql.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM giao_dich WHERE invoice_id=? AND status='SUCCESS'
                """, BigDecimal.class, invoiceId);
        BigDecimal paid = (BigDecimal) invoice.get("total_paid");
        if (received.compareTo(paid) < 0) throw new IllegalStateException("Cần đối soát số tiền thực thu trước khi gửi yêu cầu.");
        return new Context(id, studentId, classId, classCode, paid);
    }

    private Progress eligibility(Context context) {
        var counts = sql.queryForMap("""
                SELECT COUNT(*) AS total,
                  COALESCE(SUM(TIMESTAMP(lesson_date,end_time) <= CURRENT_TIMESTAMP),0) AS elapsed
                FROM lich_hoc WHERE class_id=? AND status <> 'CANCELLED'
                """, context.classId());
        long total = ((Number) counts.get("total")).longValue();
        long elapsed = ((Number) counts.get("elapsed")).longValue();
        if (total == 0) throw new IllegalStateException("Lớp chưa có lịch học để tính tỷ lệ đã học.");
        if (elapsed * 2 > total) throw new IllegalStateException("Đã qua hơn 50% số buổi, không đủ điều kiện hủy hoặc bảo lưu.");
        return new Progress(total, elapsed);
    }

    private record Context(String enrollmentId, String studentId, String classId, String classCode, BigDecimal paid) {}
    private record Progress(long total, long elapsed) {}
}
