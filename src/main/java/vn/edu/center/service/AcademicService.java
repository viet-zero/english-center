package vn.edu.center.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AcademicService {
    private final JdbcTemplate sql;

    public AcademicService(JdbcTemplate sql) {
        this.sql = sql;
    }

    public List<Map<String, Object>> teacherClasses(String teacher) {
        return sql.queryForList("SELECT id, class_code FROM lop_hoc WHERE teacher_id = ? ORDER BY class_code", teacher);
    }

    public List<Map<String, Object>> studentClasses(String student) {
        return sql.queryForList("""
                SELECT c.id, c.class_code FROM lop_hoc c
                JOIN dang_ky_hoc_phan e ON e.class_id = c.id
                WHERE e.student_id = ? AND e.status IN ('CONFIRMED','COMPLETED')
                ORDER BY c.class_code
                """, student);
    }

    public Map<String, Object> teacherView(String teacher, String classId) {
        requireTeacher(teacher, classId, false);
        return Map.of("exams", exams(classId), "lessons", lessons(classId),
                "students", sql.queryForList("""
                        SELECT e.id, u.full_name FROM dang_ky_hoc_phan e
                        JOIN hoc_vien s ON s.id = e.student_id
                        JOIN nguoi_dung u ON u.id = s.user_id
                        WHERE e.class_id = ? AND e.status IN ('CONFIRMED','COMPLETED')
                        ORDER BY u.full_name, e.id
                        """, classId),
                "attendance", sql.queryForList("""
                        SELECT a.lesson_id, a.enrollment_id, a.attendance_status
                        FROM diem_danh a JOIN lich_hoc l ON l.id = a.lesson_id WHERE l.class_id = ?
                        """, classId),
                "grades", sql.queryForList("""
                        SELECT g.exam_id, g.enrollment_id, g.score
                        FROM diem_thi g JOIN lich_thi x ON x.id = g.exam_id WHERE x.class_id = ?
                        """, classId));
    }

    public Map<String, Object> studentView(String student, String classId) {
        String enrollment = sql.queryForList("""
                SELECT id FROM dang_ky_hoc_phan
                WHERE class_id = ? AND student_id = ? AND status IN ('CONFIRMED','COMPLETED')
                """, String.class, classId, student).stream().findFirst().orElseThrow(AcademicService::forbidden);
        return Map.of("exams", exams(classId), "lessons", lessons(classId),
                "attendance", sql.queryForList("""
                        SELECT lesson_id, attendance_status FROM diem_danh WHERE enrollment_id = ?
                        """, enrollment),
                "grades", sql.queryForList("SELECT exam_id, score FROM diem_thi WHERE enrollment_id = ?", enrollment));
    }

    @Transactional
    public String createExam(String teacher, String classId, String title,
            LocalDateTime startsAt, LocalDateTime endsAt, String room) {
        requireTeacher(teacher, classId, true);
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Invalid exam time");
        }
        Integer conflicts = sql.queryForObject("""
                SELECT COUNT(*) FROM lich_thi WHERE class_id = ? AND starts_at < ? AND ends_at > ?
                """, Integer.class, classId, endsAt, startsAt);
        if (conflicts > 0) {
            throw new IllegalStateException("Exam overlaps another exam for this class");
        }
        String id = UUID.randomUUID().toString();
        sql.update("INSERT INTO lich_thi (id,class_id,title,starts_at,ends_at,room) VALUES (?,?,?,?,?,?)",
                id, classId, title.strip(), startsAt, endsAt, room.strip());
        return id;
    }

    @Transactional
    public void markAttendance(String teacher, String classId, String lessonId, String enrollmentId, String status) {
        requireTeacher(teacher, classId, true);
        requireEnrollment(classId, enrollmentId);
        if (sql.queryForList("SELECT id FROM lich_hoc WHERE id = ? AND class_id = ? AND status <> 'CANCELLED'",
                String.class, lessonId, classId).isEmpty()) {
            throw forbidden();
        }
        sql.update("""
                INSERT INTO diem_danh (lesson_id,enrollment_id,attendance_status) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE attendance_status = ?
                """, lessonId, enrollmentId, status, status);
    }

    @Transactional
    public void grade(String teacher, String classId, String examId, String enrollmentId, BigDecimal score) {
        requireTeacher(teacher, classId, true);
        requireEnrollment(classId, enrollmentId);
        if (sql.queryForList("SELECT id FROM lich_thi WHERE id = ? AND class_id = ?",
                String.class, examId, classId).isEmpty()) {
            throw forbidden();
        }
        sql.update("""
                INSERT INTO diem_thi (exam_id,enrollment_id,score) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE score = ?
                """, examId, enrollmentId, score, score);
    }


    public Map<String, Object> teacherContent(String teacher, String classId) {
        requireTeacher(teacher, classId, false);
        return Map.of("items", content(classId), "submissions", sql.queryForList("""
                SELECT b.content_id, b.enrollment_id, u.full_name, b.answer
                FROM bai_nop b JOIN noi_dung_lop n ON n.id = b.content_id
                JOIN dang_ky_hoc_phan e ON e.id = b.enrollment_id AND e.class_id = n.class_id
                JOIN hoc_vien s ON s.id = e.student_id
                JOIN nguoi_dung u ON u.id = s.user_id
                WHERE n.class_id = ? ORDER BY u.full_name, e.id
                """, classId));
    }

    public Map<String, Object> studentContent(String student, String classId) {
        String enrollment = studentEnrollment(student, classId, false);
        return Map.of("items", content(classId), "submissions", sql.queryForList("""
                SELECT b.content_id, b.answer
                FROM bai_nop b JOIN noi_dung_lop n ON n.id = b.content_id
                WHERE b.enrollment_id = ? AND n.class_id = ?
                """, enrollment, classId));
    }

    @Transactional
    public void publishContent(String teacher, String classId, String kind, String title, String content) {
        requireTeacher(teacher, classId, true);
        sql.update("INSERT INTO noi_dung_lop (id,class_id,kind,title,content) VALUES (?,?,?,?,?)",
                UUID.randomUUID().toString(), classId, kind, title.strip(), content.strip());
    }

    @Transactional
    public void submitAssignment(String student, String classId, String assignmentId, String answer) {
        String enrollment = studentEnrollment(student, classId, true);
        if (sql.queryForList("""
                SELECT id FROM noi_dung_lop WHERE id = ? AND class_id = ? AND kind = 'ASSIGNMENT'
                """, String.class, assignmentId, classId).isEmpty()) {
            throw forbidden();
        }
        sql.update("""
                INSERT INTO bai_nop (content_id,enrollment_id,answer) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE answer = ?, submitted_at = CURRENT_TIMESTAMP
                """, assignmentId, enrollment, answer.strip(), answer.strip());
    }

    private String studentEnrollment(String student, String classId, boolean lock) {
        String query = """
                SELECT id FROM dang_ky_hoc_phan
                WHERE class_id = ? AND student_id = ? AND status IN ('CONFIRMED','COMPLETED')
                """;
        return sql.queryForList(lock ? query + " FOR UPDATE" : query, String.class, classId, student)
                .stream().findFirst().orElseThrow(AcademicService::forbidden);
    }

    private List<Map<String, Object>> content(String classId) {
        return sql.queryForList("""
                SELECT id, kind, title, content FROM noi_dung_lop
                WHERE class_id = ? ORDER BY kind, title, id
                """, classId);
    }

    private List<Map<String, Object>> exams(String classId) {
        return sql.queryForList("""
                SELECT x.id,x.title, DATE_FORMAT(x.starts_at, '%Y-%m-%d %H:%i') AS starts_at,
                       DATE_FORMAT(ends_at, '%Y-%m-%d %H:%i') AS ends_at,x.room,x.exam_format,u.full_name AS invigilator
                FROM lich_thi x LEFT JOIN nhan_vien n ON n.id = x.invigilator_id LEFT JOIN nguoi_dung u ON u.id = n.user_id WHERE x.class_id = ? ORDER BY x.starts_at
                """, classId);
    }

    private List<Map<String, Object>> lessons(String classId) {
        return sql.queryForList("""
                SELECT id, CAST(lesson_date AS CHAR) AS lesson_date,
                       TIME_FORMAT(start_time, '%H:%i') AS start_time,
                       TIME_FORMAT(end_time, '%H:%i') AS end_time FROM lich_hoc
                WHERE class_id = ? AND status <> 'CANCELLED' ORDER BY lesson_date,start_time
                """, classId);
    }

    public List<Map<String, Object>> attendanceRoster(String teacher, String classId, String lessonId) {
        requireTeacher(teacher, classId, false);
        requireLesson(classId, lessonId);
        return sql.queryForList("""
                SELECT e.id, u.full_name, s.student_code, a.attendance_status
                FROM dang_ky_hoc_phan e
                JOIN hoc_vien s ON s.id = e.student_id
                JOIN nguoi_dung u ON u.id = s.user_id
                LEFT JOIN diem_danh a ON a.enrollment_id = e.id AND a.lesson_id = ?
                WHERE e.class_id = ? AND e.status IN ('CONFIRMED','COMPLETED')
                ORDER BY u.full_name, e.id
                """, lessonId, classId);
    }

    @Transactional
    public void saveAttendance(String teacher, String classId, String lessonId, Map<String, String> statuses) {
        requireTeacher(teacher, classId, true);
        requireLesson(classId, lessonId);
        var roster = sql.queryForList("""
                SELECT id FROM dang_ky_hoc_phan
                WHERE class_id = ? AND status IN ('CONFIRMED','COMPLETED') FOR UPDATE
                """, String.class, classId);
        if (!roster.containsAll(statuses.keySet())) {
            throw forbidden();
        }
        if (roster.size() != statuses.size()) {
            throw new IllegalStateException("Class roster changed. Reopen attendance and try again.");
        }
        sql.batchUpdate("""
                INSERT INTO diem_danh (lesson_id,enrollment_id,attendance_status) VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE attendance_status = ?
                """, roster.stream().map(id -> new Object[] {lessonId, id, statuses.get(id), statuses.get(id)}).toList());
    }

    private void requireLesson(String classId, String lessonId) {
        if (sql.queryForList("SELECT id FROM lich_hoc WHERE id = ? AND class_id = ? AND status <> 'CANCELLED'",
                String.class, lessonId, classId).isEmpty()) {
            throw forbidden();
        }
    }

    private void requireTeacher(String teacher, String classId, boolean lock) {
        String query = "SELECT id FROM lop_hoc WHERE id = ? AND teacher_id = ?";
        if (sql.queryForList(lock ? query + " FOR UPDATE" : query, String.class, classId, teacher).isEmpty()) {
            throw forbidden();
        }
    }

    private void requireEnrollment(String classId, String enrollmentId) {
        if (sql.queryForList("""
                SELECT id FROM dang_ky_hoc_phan
                WHERE id = ? AND class_id = ? AND status IN ('CONFIRMED','COMPLETED') FOR UPDATE
                """, String.class, enrollmentId, classId).isEmpty()) {
            throw forbidden();
        }
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Class access denied");
    }
}
