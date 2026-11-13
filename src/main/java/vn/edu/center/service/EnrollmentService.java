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

    public EnrollmentService(StudentRepository students, ClassroomRepository classes,
            ModuleRepository modules, CourseRepository courses, EnrollmentRepository enrollments,
            InvoiceRepository invoices, InvoiceItemRepository items, JdbcTemplate sql) {
        this.sql = sql;
        this.students = students;
        this.classes = classes;
        this.modules = modules;
        this.courses = courses;
        this.enrollments = enrollments;
        this.invoices = invoices;
        this.items = items;
    }

    // studentId must come from the authenticated user, not an unchecked HTTP parameter.
    // All registration writers must lock student first, then class.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationResult register(String studentId, String classId) {
        Student student = students.findLockedById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        Classroom classroom = classes.findLockedById(classId)
                .orElseThrow(() -> new IllegalArgumentException("Class not found"));
        Enrollment existing = enrollments.findByStudentIdAndClassId(studentId, classId).orElse(null);
        if (existing != null) {
            if ("CANCELLED".equals(existing.getStatus())) {
                throw new IllegalStateException("Cancelled registration requires staff review");
            }
            InvoiceItem item = items.findByEnrollmentId(existing.getId())
                    .orElseThrow(() -> new IllegalStateException("Registration invoice is missing"));
            return new RegistrationResult(existing.getId(), item.getInvoiceId());
        }
        verifyAvailable(student, classroom);
        var hold = sql.queryForList("""
                SELECT class_id, CAST(expires_at > CURRENT_TIMESTAMP AS UNSIGNED) AS active
                FROM giu_cho WHERE student_id = ?
                """, studentId);
        if (!hold.isEmpty() && classId.equals(hold.getFirst().get("class_id"))
                && ((Number) hold.getFirst().get("active")).intValue() == 0) {
            throw new IllegalStateException("Giữ chỗ đã hết hạn. Hãy giữ chỗ lại.");
        }
        sql.update("DELETE FROM giu_cho WHERE student_id = ?", studentId);
        Module module = modules.findById(classroom.getModuleId()).orElseThrow();
        return createRegistration(studentId, classId, module.getTuition());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String hold(String studentId, String classId) {
        Student student = students.findLockedById(studentId).orElseThrow();
        Classroom classroom = classes.findLockedById(classId).orElseThrow();
        var current = sql.queryForList("""
                SELECT DATE_FORMAT(expires_at, '%Y-%m-%dT%H:%i:%s')
                FROM giu_cho WHERE student_id = ? AND class_id = ? AND expires_at > CURRENT_TIMESTAMP
                """, String.class, studentId, classId);
        if (!current.isEmpty()) {
            return current.getFirst();
        }
        if (enrollments.findByStudentIdAndClassId(studentId, classId).isPresent()) {
            throw new IllegalStateException("Lớp này đã có đăng ký của bạn.");
        }
        verifyAvailable(student, classroom);
        sql.update("""
                INSERT INTO giu_cho (student_id, class_id, expires_at)
                VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL 15 MINUTE)
                ON DUPLICATE KEY UPDATE class_id = ?, expires_at = CURRENT_TIMESTAMP + INTERVAL 15 MINUTE
                """, studentId, classId, classId);
        return sql.queryForObject("""
                SELECT DATE_FORMAT(expires_at, '%Y-%m-%dT%H:%i:%s') FROM giu_cho WHERE student_id = ?
                """, String.class, studentId);
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
        if (enrollments.countActiveModule(studentId, module.getId()) > 0) {
            throw new IllegalStateException("Module is already registered");
        }
        long heldByOthers = sql.queryForObject("""
                SELECT COUNT(*) FROM giu_cho
                WHERE class_id = ? AND student_id <> ? AND expires_at > CURRENT_TIMESTAMP
                """, Long.class, classId, studentId);
        if (enrollments.countOccupiedSeats(classId) + heldByOthers >= classroom.getMaxStudents()) {
            throw new IllegalStateException("Class is full");
        }
        if (enrollments.countScheduleConflicts(studentId, classId) > 0) {
            throw new IllegalStateException("Class schedule overlaps existing registration");
        }
    }

    private RegistrationResult createRegistration(String studentId, String classId, BigDecimal tuition) {
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
        invoice.setDiscount(BigDecimal.ZERO);
        invoice.setTotalDue(tuition);
        invoice.setTotalPaid(BigDecimal.ZERO);
        invoice.setStatus(tuition.signum() == 0 ? "PAID" : "UNPAID");
        invoices.saveAndFlush(invoice);

        InvoiceItem item = new InvoiceItem();
        item.setId(UUID.randomUUID().toString());
        item.setInvoiceId(invoice.getId());
        item.setEnrollmentId(enrollment.getId());
        item.setAmount(tuition);
        item.setDiscount(BigDecimal.ZERO);
        item.setTotal(tuition);
        items.saveAndFlush(item);
        return new RegistrationResult(enrollment.getId(), invoice.getId());
    }

    public record RegistrationResult(String enrollmentId, String invoiceId) {}
}
