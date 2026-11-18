package vn.edu.center.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.center.entity.*;
import vn.edu.center.entity.Module;
import vn.edu.center.repository.*;

@Service
public class EnrollmentService {
    private final JdbcTemplate sql;
    private final StudentRepository students;
    private final ClassroomRepository classes;
    private final ModuleRepository modules;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final NotificationService notifications;
    private final CashShiftService shifts;
    private final SePayClient sepay;
    private final CouponService coupons;

    public EnrollmentService(StudentRepository students, ClassroomRepository classes,
            ModuleRepository modules, CourseRepository courses, EnrollmentRepository enrollments,
            InvoiceRepository invoices, InvoiceItemRepository items, JdbcTemplate sql,
            NotificationService notifications, CashShiftService shifts, SePayClient sepay, CouponService coupons) {
        this.sql = sql;
        this.students = students;
        this.classes = classes;
        this.modules = modules;
        this.courses = courses;
        this.enrollments = enrollments;
        this.invoices = invoices;
        this.items = items;
        this.notifications = notifications;
        this.shifts = shifts;
        this.sepay = sepay;
        this.coupons = coupons;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Checkout openCheckout(String studentId, String classId, String couponCode) {
        Student student = students.findLockedById(studentId).orElseThrow();
        Classroom classroom = classes.findLockedById(classId).orElseThrow();
        verifyAvailable(student, classroom);
        int amount;
        try {
            amount = modules.findById(classroom.getModuleId()).orElseThrow().getTuition().intValueExact();
        } catch (ArithmeticException error) {
            throw new IllegalStateException("Học phí phải là số nguyên VND.");
        }
        if (amount <= 0) throw new IllegalStateException("Học phí phải lớn hơn 0.");
        long code = UUID.randomUUID().getLeastSignificantBits() & ((1L << 52) - 1);
        var pricing=coupons.price(studentId,courseId(classroom),amount,couponCode);
        var link=pricing.due()==0 ? new SePayClient.Link("free-"+code,null,null) : sepay.create(code,pricing.due());
        sql.update("""
                INSERT INTO don_thanh_toan(order_code,student_id,class_id,amount,provider,link_id,checkout_url,receiving_account,
                    tuition_amount,discount_amount,coupon_id,coupon_code)
                VALUES (?,?,?,?,'SEPAY',?,?,?,?,?,?,?)
                """,code,studentId,classId,pricing.due(),link.paymentLinkId(),link.checkoutUrl(),link.accountNumber(),
                pricing.tuition(),pricing.discount(),pricing.couponId(),pricing.code());
        return checkout(studentId,code);
    }
    public Checkout checkout(String studentId, long code) {
        return sql.query("""
                SELECT order_code, amount, status, checkout_url, invoice_id, provider, receiving_account, tuition_amount, discount_amount, coupon_code
                FROM don_thanh_toan WHERE order_code = ? AND student_id = ?
                """, (row, index) -> new Checkout(row.getLong("order_code"), row.getInt("amount"),
                row.getString("status"), row.getString("checkout_url"), row.getString("invoice_id"), row.getString("provider"),
                "SEPAY".equals(row.getString("provider")) ? "EC"+code : null,
                "SEPAY".equals(row.getString("provider")) ? row.getString("checkout_url") : null,
                row.getString("receiving_account"),row.getInt("tuition_amount"),row.getInt("discount_amount"),row.getString("coupon_code")),
                code, studentId).stream().findFirst().orElseThrow(() ->
                        new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND));
    }

    public java.util.List<java.util.Map<String, Object>> paymentReviews() {
        return sql.queryForList("""
                SELECT r.order_code AS orderCode, r.reference, r.amount AS receivedAmount,
                       o.amount AS expectedAmount, r.reason, r.resolution, r.resolved_by AS resolvedBy, r.resolution_note AS resolutionNote, r.resolved_at AS resolvedAt,
                       r.target_class_id AS targetClassId, r.resolved_invoice_id AS resolvedInvoiceId,
                       o.student_id AS studentId,
                       c.class_code AS classCode, r.received_at AS receivedAt
                FROM bien_nhan_thanh_toan r JOIN don_thanh_toan o ON o.order_code = r.order_code
                JOIN lop_hoc c ON c.id = o.class_id
                WHERE r.reason IS NOT NULL ORDER BY r.received_at DESC
                """);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void settle(long code, String linkId, String reference, long amount) {
        Order order=lockOrder(code);
        if (!linkId.equals(order.linkId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Payment link is not associated with this order");
        }
        if (sql.queryForObject("SELECT COUNT(*) FROM bien_nhan_thanh_toan WHERE reference = ?",
                Long.class, reference) > 0) return;

        Student student = students.findLockedById(order.studentId()).orElseThrow();
        Classroom classroom = classes.findLockedById(order.classId()).orElseThrow();
        String reason = reviewReason(order, student, classroom, amount);
        sql.update("INSERT INTO bien_nhan_thanh_toan (reference, order_code, amount, reason) VALUES (?, ?, ?, ?)",
                reference, code, amount, reason);
        if (reason != null) {
            int updated = sql.update("UPDATE don_thanh_toan SET status = 'REVIEW' WHERE order_code = ? AND status = 'PENDING'", code);
            if (updated == 1) {
                notifications.notifyStudent(order.studentId(), "Kế toán đang đối soát giao dịch thanh toán " + code + ".");
            }
            return;
        }
        String invoiceId = createRegistration(order.studentId(), order.classId(), order.pricing());
        coupons.consume(order.pricing(),order.studentId(),code,invoiceId);
        sql.update("""
                INSERT INTO giao_dich (id, invoice_id, amount, paid_at, method, reference_code, status)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'BANK_TRANSFER', ?, 'SUCCESS')
                """, UUID.randomUUID().toString(), invoiceId, amount, reference);
        sql.update("UPDATE don_thanh_toan SET status = 'ENROLLED', invoice_id = ? WHERE order_code = ?", invoiceId, code);
        notifications.notifyStudent(order.studentId(),
                "Đã thanh toán đủ và đăng ký thành công lớp " + classroom.getClassCode() + ".");
    }

    private String reviewReason(Order order, Student student, Classroom classroom, long amount) {
        if (!"PENDING".equals(order.status())) return "Đơn đã được xử lý hoặc đang chờ đối soát.";
        if (amount != order.amount()) return "Số tiền nhận được khác học phí.";
        try {
            verifyAvailable(student, classroom);
            coupons.validateSaved(order.studentId(),courseId(classroom),order.pricing());
            return null;
        } catch (IllegalStateException error) {
            return error.getMessage();
        }
    }

    public java.util.List<java.util.Map<String, Object>> reviewClasses(String reference) {
        return sql.queryForList("""
                SELECT c.id, c.class_code AS classCode,
                       c.max_students - (SELECT COUNT(*) FROM dang_ky_hoc_phan e
                         WHERE e.class_id = c.id AND e.status IN ('CONFIRMED', 'COMPLETED')) AS remainingSeats
                FROM bien_nhan_thanh_toan r JOIN don_thanh_toan o ON o.order_code = r.order_code
                JOIN lop_hoc original ON original.id = o.class_id
                JOIN lop_hoc c ON c.module_id = original.module_id
                WHERE r.reference = ? AND r.reason IS NOT NULL AND r.resolution IS NULL
                  AND c.status = 'OPEN' AND c.id <> original.id
                HAVING remainingSeats > 0 ORDER BY c.class_code
                """, reference);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void resolveReview(String reference, String employeeId, String action, String targetId, String note) {
        if ("TRANSFER".equals(action) && (targetId == null || targetId.isBlank())) {
            throw new IllegalArgumentException("Hãy chọn lớp nhận.");
        }
        Long code = sql.query("SELECT order_code FROM bien_nhan_thanh_toan WHERE reference = ?",
                (row, index) -> row.getLong(1), reference).stream().findFirst().orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        var order = sql.queryForMap("SELECT * FROM don_thanh_toan WHERE order_code = ? FOR UPDATE", code);
        var receipt = sql.queryForMap("SELECT * FROM bien_nhan_thanh_toan WHERE reference = ? FOR UPDATE", reference);
        if (receipt.get("reason") == null) throw new IllegalStateException("Giao dịch không cần đối soát.");
        if (receipt.get("resolution") != null) {
            if (action.equals(receipt.get("resolution"))
                    && (!"TRANSFER".equals(action) || java.util.Objects.equals(targetId, receipt.get("target_class_id")))) return;
            throw new IllegalStateException("Giao dịch đã được xử lý.");
        }
        String studentId = (String) order.get("student_id");
        String invoiceId = null;
        if ("TRANSFER".equals(action)) {
            if (!"REVIEW".equals(order.get("status")) || order.get("invoice_id") != null) {
                throw new IllegalStateException("Đơn đã có kết quả xử lý, hãy đối soát hoàn tiền.");
            }
            Student student = students.findLockedById(studentId).orElseThrow();
            Classroom target = classes.findLockedById(targetId).orElseThrow(() ->
                    new IllegalArgumentException("Không tìm thấy lớp nhận."));
            Classroom original = classes.findById((String) order.get("class_id")).orElseThrow();
            if (target.getId().equals(original.getId()) || !target.getModuleId().equals(original.getModuleId())) {
                throw new IllegalStateException("Chọn lớp khác cùng học phần của khóa học.");
            }
            BigDecimal received = new BigDecimal(receipt.get("amount").toString());
            var pricing=pricing(order);
            if (received.compareTo(BigDecimal.valueOf(pricing.due()))!=0) {
                throw new IllegalStateException("Khoản đã nhận phải bằng học phí sau giảm giá của đơn.");
            }
            verifyAvailable(student, target);
            coupons.validateSaved(studentId,courseId(target),pricing);
            invoiceId = createRegistration(studentId, targetId, pricing);
            coupons.consume(pricing,studentId,code,invoiceId);
            sql.update("""
                    INSERT INTO giao_dich(id, invoice_id, collected_by, amount, paid_at, method, reference_code, status)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, 'BANK_TRANSFER', ?, 'SUCCESS')
                    """, UUID.randomUUID().toString(), invoiceId, employeeId, received, reference);
            sql.update("UPDATE don_thanh_toan SET status = 'ENROLLED', invoice_id = ? WHERE order_code = ?", invoiceId, code);
            notifications.notifyStudent(studentId, "Kế toán đã chuyển khoản thanh toán sang lớp " + target.getClassCode() + ".");
        } else if ("REFUND_DEMO".equals(action)) {
            if (!java.util.Set.of("demo-" + code,"sepay-demo-" + code).contains(order.get("link_id"))) {
                throw new IllegalStateException("Chỉ ghi nhận hoàn tiền demo cho giao dịch demo.");
            }
            String shiftId=shifts.requireOpen(employeeId);
            sql.update("UPDATE bien_nhan_thanh_toan SET refund_shift_id=? WHERE reference=?",shiftId,reference);
            sql.update("UPDATE don_thanh_toan SET status = 'REFUNDED' WHERE order_code = ? AND status = 'REVIEW'", code);
            notifications.notifyStudent(studentId, "Đã ghi nhận hoàn tiền demo cho đơn " + code + ".");
        } else {
            throw new IllegalArgumentException("Thao tác không hợp lệ.");
        }
        sql.update("""
                UPDATE bien_nhan_thanh_toan SET resolution = ?, resolved_by = ?, resolved_at = CURRENT_TIMESTAMP,
                target_class_id = ?, resolved_invoice_id = ?, resolution_note = ? WHERE reference = ?
                """, action, employeeId, "TRANSFER".equals(action) ? targetId : null, invoiceId, note, reference);
    }
    public record Checkout(long orderCode, int amount, String status, String checkoutUrl, String invoiceId, String provider, String paymentCode, String qrUrl, String receivingAccount, int tuitionAmount, int discountAmount, String couponCode) {}
    private record Order(String studentId, String classId, int amount, String linkId, String status, CouponService.Pricing pricing) {}

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Checkout confirmFree(String studentId,long code) {
        Order order=lockOrder(code);
        if (!studentId.equals(order.studentId())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        if (order.amount()!=0) throw new IllegalStateException("Đơn cần thanh toán qua SePay.");
        if ("ENROLLED".equals(order.status())) return checkout(studentId,code);
        if (!"PENDING".equals(order.status())) throw new IllegalStateException("Đơn đang chờ đối soát.");
        Student student=students.findLockedById(studentId).orElseThrow();
        Classroom classroom=classes.findLockedById(order.classId()).orElseThrow();
        verifyAvailable(student,classroom);
        coupons.validateSaved(studentId,courseId(classroom),order.pricing());
        String invoice=createRegistration(studentId,order.classId(),order.pricing());
        coupons.consume(order.pricing(),studentId,code,invoice);
        sql.update("UPDATE don_thanh_toan SET status='ENROLLED',invoice_id=? WHERE order_code=?",invoice,code);
        notifications.notifyStudent(studentId,"Đã xác nhận học phí 0 ₫ và đăng ký lớp "+classroom.getClassCode()+".");
        return checkout(studentId,code);
    }

    private Order lockOrder(long code) {
        var row=sql.queryForList("SELECT * FROM don_thanh_toan WHERE order_code=? FOR UPDATE",code).stream().findFirst()
                .orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        return new Order((String)row.get("student_id"),(String)row.get("class_id"),((Number)row.get("amount")).intValue(),
                (String)row.get("link_id"),(String)row.get("status"),pricing(row));
    }

    private CouponService.Pricing pricing(java.util.Map<String,Object> row) {
        return new CouponService.Pricing(((Number)row.get("tuition_amount")).intValue(),
                ((Number)row.get("discount_amount")).intValue(),(String)row.get("coupon_id"),(String)row.get("coupon_code"));
    }

    private String courseId(Classroom classroom) {
        return modules.findById(classroom.getModuleId()).orElseThrow().getCourseId();
    }
    private void verifyAvailable(Student student, Classroom classroom) {
        String studentId = student.getId();
        String classId = classroom.getId();
        if (!"ACTIVE".equals(student.getStatus()) || !"OPEN".equals(classroom.getStatus())) {
            throw new IllegalStateException("Student or class is not open for registration");
        }
        Module module = modules.findById(classroom.getModuleId()).orElseThrow();
        Course course = courses.findById(module.getCourseId()).orElseThrow();
        if (!"ACTIVE".equals(module.getStatus()) || !"ACTIVE".equals(course.getStatus())) {
            throw new IllegalStateException("Course or module is inactive");
        }
        if (module.getSequenceNumber() > 1 && enrollments.countCompletedPrerequisite(
                studentId, course.getId(), module.getSequenceNumber() - 1) == 0) {
            throw new IllegalStateException("Prerequisite is not completed");
        }
        if (enrollments.findByStudentIdAndClassId(studentId, classId).isPresent()) {
            throw new IllegalStateException("Bạn đã có đăng ký cho lớp này.");
        }
        if (enrollments.countActiveModule(studentId, module.getId()) > 0) {
            throw new IllegalStateException("Module is already registered");
        }
        if (enrollments.countOccupiedSeats(classId) >= classroom.getMaxStudents()) {
            throw new IllegalStateException("Lớp đã hết chỗ.");
        }
        if (enrollments.countScheduleConflicts(studentId, classId) > 0) {
            throw new IllegalStateException("Class schedule overlaps existing registration");
        }
    }

    private String createRegistration(String studentId, String classId, CouponService.Pricing pricing) {
        BigDecimal tuition=BigDecimal.valueOf(pricing.tuition());
        BigDecimal discount=BigDecimal.valueOf(pricing.discount());
        BigDecimal due=BigDecimal.valueOf(pricing.due());
        if (tuition == null || tuition.signum() < 0) {
            throw new IllegalStateException("Invalid module tuition");
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setId(UUID.randomUUID().toString());
        enrollment.setStudentId(studentId);
        enrollment.setClassId(classId);
        enrollment.setRegisteredAt(LocalDateTime.now());
        enrollment.setAppliedTuition(tuition);
        enrollment.setStatus("CONFIRMED");
        enrollments.saveAndFlush(enrollment);

        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID().toString());
        invoice.setStudentId(studentId);
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setDiscount(discount);
        invoice.setVoucherId(pricing.couponId());
        invoice.setTotalDue(due);
        invoice.setTotalPaid(due);
        invoice.setStatus("PAID");
        invoices.saveAndFlush(invoice);

        InvoiceItem item = new InvoiceItem();
        item.setId(UUID.randomUUID().toString());
        item.setInvoiceId(invoice.getId());
        item.setEnrollmentId(enrollment.getId());
        item.setAmount(tuition);
        item.setDiscount(discount);
        item.setTotal(due);
        items.saveAndFlush(item);
        return invoice.getId();
    }
}
