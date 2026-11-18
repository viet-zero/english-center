package vn.edu.center.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompletionService {
    private final JdbcTemplate sql;
    private final CertificatePdf pdf;
    private final NotificationService notifications;

    public CompletionService(JdbcTemplate sql, CertificatePdf pdf, NotificationService notifications) {
        this.sql = sql;
        this.pdf = pdf;
        this.notifications = notifications;
    }

    public List<Map<String,Object>> classes(String teacher) {
        return sql.queryForList("""
                SELECT c.id,c.class_code AS classCode,c.finalized_at AS finalizedAt,m.name AS moduleName
                FROM lop_hoc c JOIN hoc_phan m ON m.id=c.module_id
                """ + (teacher == null ? "" : " WHERE c.teacher_id=?") + " ORDER BY c.class_code",
                teacher == null ? new Object[]{} : new Object[]{teacher});
    }

    public Map<String,Object> preview(String classId, String teacher) {
        var classroom = classroom(classId,false);
        if (teacher != null && !teacher.equals(classroom.get("teacher_id"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var exams = sql.queryForList("SELECT id,title,grade_weight AS weight FROM lich_thi WHERE class_id=? ORDER BY starts_at,id",classId);
        var lessons = sql.queryForMap("""
                SELECT COUNT(*) AS total,
                    COALESCE(SUM(TIMESTAMP(lesson_date,end_time)>CURRENT_TIMESTAMP),0) AS future
                FROM lich_hoc WHERE class_id=? AND status<>'CANCELLED'
                """,classId);
        boolean ended = ((java.sql.Date) classroom.get("end_date")).toLocalDate().compareTo(java.time.LocalDate.now()) <= 0
                && ((Number) lessons.get("future")).longValue()==0
                && sql.queryForObject("SELECT COUNT(*) FROM lich_thi WHERE class_id=? AND ends_at>CURRENT_TIMESTAMP",Long.class,classId)==0;
        long total = ((Number) lessons.get("total")).longValue();
        var students = sql.queryForList("""
                SELECT e.id AS enrollmentId,e.student_id AS studentId,u.full_name AS fullName,e.status AS enrollmentStatus,
                  (SELECT COUNT(*) FROM diem_thi g JOIN lich_thi x ON x.id=g.exam_id
                   WHERE x.class_id=e.class_id AND g.enrollment_id=e.id) AS graded,
                  (SELECT ROUND(SUM(g.score*x.grade_weight)/SUM(x.grade_weight),2)
                   FROM lich_thi x JOIN diem_thi g ON g.exam_id=x.id AND g.enrollment_id=e.id WHERE x.class_id=e.class_id) AS finalScore,
                  (SELECT COUNT(*) FROM diem_danh a JOIN lich_hoc l ON l.id=a.lesson_id
                   WHERE a.enrollment_id=e.id AND l.class_id=e.class_id AND l.status<>'CANCELLED') AS marked,
                  (SELECT COUNT(*) FROM diem_danh a JOIN lich_hoc l ON l.id=a.lesson_id
                   WHERE a.enrollment_id=e.id AND l.class_id=e.class_id AND l.status<>'CANCELLED'
                     AND a.attendance_status IN ('PRESENT','LATE')) AS attended
                FROM dang_ky_hoc_phan e JOIN hoc_vien s ON s.id=e.student_id JOIN nguoi_dung u ON u.id=s.user_id
                WHERE e.class_id=? AND e.status IN ('CONFIRMED','COMPLETED','FAILED') ORDER BY u.full_name,e.id
                """,classId);
        for (var row : students) {
            String enrollment = (String)row.get("enrollmentId");
            long attended = ((Number)row.get("attended")).longValue();
            BigDecimal attendance = total==0 ? BigDecimal.ZERO : BigDecimal.valueOf(attended*100)
                    .divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP);
            boolean gradesComplete = !exams.isEmpty() && ((Number)row.get("graded")).longValue()==exams.size();
            boolean marked = total>0 && ((Number)row.get("marked")).longValue()==total;
            boolean paid = paid(enrollment);
            BigDecimal score=(BigDecimal)row.get("finalScore");
            boolean academic = gradesComplete && marked && score.compareTo((BigDecimal)classroom.get("completion_pass_score"))>=0
                    && BigDecimal.valueOf(attended*100).compareTo(((BigDecimal)classroom.get("completion_attendance"))
                            .multiply(BigDecimal.valueOf(total)))>=0;
            row.put("attendancePercent",attendance);
            row.put("totalLessons",total);
            row.put("missingGrades",exams.size()-((Number)row.get("graded")).longValue());
            row.put("missingAttendance",total-((Number)row.get("marked")).longValue());
            row.put("paid",paid);
            row.put("ready",ended && gradesComplete && marked);
            row.put("decision",academic ? paid ? "PASS" : "FINANCIAL_HOLD" : "FAIL");
            if (classroom.get("finalized_at")!=null) {
                var saved=sql.queryForList("SELECT * FROM ket_qua_cuoi_khoa WHERE enrollment_id=?",enrollment);
                if (!saved.isEmpty()) {
                    row.put("decision",saved.getFirst().get("decision"));
                    row.put("finalScore",saved.getFirst().get("final_score"));
                    row.put("attendancePercent",saved.getFirst().get("attendance_percent"));
                }
            }
        }
        return Map.of("classroom",classroom,"exams",exams,"students",students,"ended",ended);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void policy(String classId, BigDecimal passScore, BigDecimal attendance, Map<String,BigDecimal> weights) {
        var classroom=classroom(classId,true);
        if (classroom.get("finalized_at")!=null) throw new IllegalStateException("Lớp đã chốt kết quả.");
        Set<String> ids=new HashSet<>(sql.queryForList("SELECT id FROM lich_thi WHERE class_id=?",String.class,classId));
        if (!ids.equals(weights.keySet())) throw new IllegalStateException("Danh sách kỳ thi đã thay đổi. Hãy tải lại.");
        sql.update("UPDATE lop_hoc SET completion_pass_score=?,completion_attendance=? WHERE id=?",passScore,attendance,classId);
        weights.forEach((id,weight)->sql.update("UPDATE lich_thi SET grade_weight=? WHERE id=? AND class_id=?",weight,id,classId));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void finalizeClass(String classId,String employee) {
        var classroom=classroom(classId,true);
        if (classroom.get("finalized_at")!=null) return;
        var view=preview(classId,null);
        @SuppressWarnings("unchecked")
        var rows=(List<Map<String,Object>>)view.get("students");
        if (rows.isEmpty() || !Boolean.TRUE.equals(view.get("ended"))
                || rows.stream().anyMatch(row->!Boolean.TRUE.equals(row.get("ready")))) {
            throw new IllegalStateException("Lớp phải kết thúc, có lịch học/kỳ thi và đủ 100% điểm, điểm danh trước khi chốt.");
        }
        var module=sql.queryForMap("SELECT course_id,duration_hours FROM hoc_phan WHERE id=?",classroom.get("module_id"));
        for (var row:rows) {
            String enrollment=(String)row.get("enrollmentId");
            sql.queryForList("SELECT id FROM dang_ky_hoc_phan WHERE id=? FOR UPDATE",enrollment);
            lockInvoice(enrollment);
            String decision=(String)row.get("decision");
            if ("PASS".equals(decision) && !paid(enrollment)) decision="FINANCIAL_HOLD";
            sql.update("""
                    INSERT INTO ket_qua_cuoi_khoa(enrollment_id,course_id,module_id,duration_hours,final_score,
                        attended_lessons,total_lessons,attendance_percent,pass_score,minimum_attendance,decision,finalized_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """,enrollment,module.get("course_id"),classroom.get("module_id"),module.get("duration_hours"),row.get("finalScore"),
                    row.get("attended"),row.get("totalLessons"),row.get("attendancePercent"),classroom.get("completion_pass_score"),
                    classroom.get("completion_attendance"),decision,employee);
            sql.update("UPDATE dang_ky_hoc_phan SET status=? WHERE id=?",
                    "PASS".equals(decision) ? "COMPLETED" : "FAIL".equals(decision) ? "FAILED" : "CONFIRMED",enrollment);
            notifications.notifyStudent((String)row.get("studentId"),"Đã chốt kết quả lớp "+classroom.get("class_code")+".");
        }
        sql.update("UPDATE lop_hoc SET finalized_at=CURRENT_TIMESTAMP,finalized_by=?,status='CLOSED' WHERE id=?",employee,classId);
        for (var row:rows) issueCourse((String)row.get("studentId"),(String)module.get("course_id"),employee);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void issue(String classId,String enrollment,String employee) {
        var classroom=classroom(classId,true);
        if (classroom.get("finalized_at")==null) throw new IllegalStateException("Lớp chưa chốt kết quả.");
        var registration=sql.queryForList("SELECT * FROM dang_ky_hoc_phan WHERE id=? AND class_id=? FOR UPDATE",enrollment,classId)
                .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        var result=sql.queryForMap("SELECT * FROM ket_qua_cuoi_khoa WHERE enrollment_id=?",enrollment);
        if ("FAIL".equals(result.get("decision"))) throw new IllegalStateException("Chưa đạt điểm hoặc chuyên cần.");
        lockInvoice(enrollment);
        if (!paid(enrollment)) throw new IllegalStateException("Học phí chưa hoàn tất hoặc đã phát sinh hoàn tiền.");
        if (!Set.of("CONFIRMED","COMPLETED").contains(registration.get("status"))) {
            throw new IllegalStateException("Đăng ký không còn hợp lệ.");
        }
        sql.update("UPDATE ket_qua_cuoi_khoa SET decision='PASS' WHERE enrollment_id=?",enrollment);
        sql.update("UPDATE dang_ky_hoc_phan SET status='COMPLETED' WHERE id=?",enrollment);
        issueCourse((String)registration.get("student_id"),(String)result.get("course_id"),employee);
    }

    private void issueCourse(String student,String course,String employee) {
        var courseInfo=sql.queryForMap("SELECT name FROM khoa_hoc WHERE id=? FOR UPDATE",course);
        if (sql.queryForObject("SELECT COUNT(*) FROM chung_chi WHERE student_id=? AND course_id=?",Long.class,student,course)>0) return;
        var modules=sql.queryForList("SELECT id FROM hoc_phan WHERE course_id=? AND status='ACTIVE' ORDER BY sequence_number",String.class,course);
        BigDecimal weighted=BigDecimal.ZERO;
        long hours=0,attended=0,total=0;
        for (String module:modules) {
            var matches=sql.queryForList("""
                    SELECT r.* FROM ket_qua_cuoi_khoa r JOIN dang_ky_hoc_phan e ON e.id=r.enrollment_id
                    WHERE e.student_id=? AND r.module_id=? AND e.status='COMPLETED' AND r.decision='PASS'
                    ORDER BY r.finalized_at DESC,r.enrollment_id
                    """,student,module);
            if (matches.isEmpty() || !paid((String)matches.getFirst().get("enrollment_id"))) {

                return;
            }
            var result=matches.getFirst();
            long duration=((Number)result.get("duration_hours")).longValue();
            weighted=weighted.add(((BigDecimal)result.get("final_score")).multiply(BigDecimal.valueOf(duration)));
            hours+=duration;
            attended+=((Number)result.get("attended_lessons")).longValue();
            total+=((Number)result.get("total_lessons")).longValue();
        }
        if (modules.isEmpty() || hours==0 || total==0) {

            return;
        }
        BigDecimal score=weighted.divide(BigDecimal.valueOf(hours),2,RoundingMode.HALF_UP);
        BigDecimal attendance=BigDecimal.valueOf(attended*100).divide(BigDecimal.valueOf(total),2,RoundingMode.HALF_UP);
        String name=sql.queryForObject("SELECT u.full_name FROM hoc_vien s JOIN nguoi_dung u ON u.id=s.user_id WHERE s.id=?",String.class,student);
        String id=UUID.randomUUID().toString(),token=UUID.randomUUID().toString();
        sql.update("""
                INSERT INTO chung_chi(id,verification_token,student_id,course_id,student_name,course_name,final_score,attendance_percent,issued_by)
                VALUES (?,?,?,?,?,?,?,?,?)
                """,id,token,student,course,name,courseInfo.get("name"),score,attendance,employee);
        String code="CC"+sql.queryForObject("SELECT certificate_number FROM chung_chi WHERE id=?",Long.class,id);
        sql.update("UPDATE chung_chi SET pdf=? WHERE id=?",pdf.create(code,token,name,(String)courseInfo.get("name"),
                score.toPlainString(),attendance.toPlainString()),id);
        notifications.notifyStudent(student,"Đã cấp chứng chỉ "+code+" cho khóa "+courseInfo.get("name")+".");
    }

    private boolean paid(String enrollment) {
        return sql.queryForObject("""
                SELECT COUNT(*) FROM chi_tiet_hoa_don d JOIN hoa_don i ON i.id=d.invoice_id
                WHERE d.enrollment_id=? AND i.status='PAID' AND i.total_paid=i.total_due
                  AND (SELECT COALESCE(SUM(g.amount),0) FROM giao_dich g WHERE g.invoice_id=i.id AND g.status='SUCCESS')>=i.total_due
                  AND NOT EXISTS(SELECT 1 FROM hoan_tien_demo f WHERE f.invoice_id=i.id)
                """,Long.class,enrollment)>0;
    }

    private void lockInvoice(String enrollment) {
        sql.queryForList("""
                SELECT i.id FROM hoa_don i JOIN chi_tiet_hoa_don d ON d.invoice_id=i.id WHERE d.enrollment_id=? FOR UPDATE
                """,enrollment);
    }

    private Map<String,Object> classroom(String id,boolean lock) {
        return sql.queryForList("SELECT * FROM lop_hoc WHERE id=?"+(lock?" FOR UPDATE":""),id)
                .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<Map<String,Object>> results(String student) {
        return sql.queryForList("""
                SELECT c.class_code AS classCode,m.name AS moduleName,r.final_score AS finalScore,
                       r.attendance_percent AS attendancePercent,r.decision,r.finalized_at AS finalizedAt
                FROM ket_qua_cuoi_khoa r JOIN dang_ky_hoc_phan e ON e.id=r.enrollment_id
                JOIN lop_hoc c ON c.id=e.class_id JOIN hoc_phan m ON m.id=r.module_id
                WHERE e.student_id=? ORDER BY r.finalized_at DESC,c.class_code
                """,student);
    }
    public List<Map<String,Object>> certificates(String student) {
        return sql.queryForList("""
                SELECT id,verification_token AS verificationToken,CONCAT('CC',certificate_number) AS code,course_name AS courseName,
                    final_score AS finalScore,attendance_percent AS attendancePercent,issued_at AS issuedAt
                FROM chung_chi WHERE student_id=? AND pdf IS NOT NULL ORDER BY issued_at DESC,certificate_number DESC
                """,student);
    }

    public byte[] download(String student,String id) {
        return sql.query("SELECT pdf FROM chung_chi WHERE id=? AND student_id=? AND pdf IS NOT NULL",
                (row,index)->row.getBytes(1),id,student).stream().findFirst()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public Map<String,Object> verify(String token) {
        return sql.queryForList("""
                SELECT CONCAT('CC',certificate_number) AS code,student_name AS studentName,
                    course_name AS courseName,issued_at AS issuedAt FROM chung_chi
                WHERE verification_token=? AND pdf IS NOT NULL
                """,token).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
