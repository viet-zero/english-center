package vn.edu.center.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.center.service.CurrentUserService;

@RestController
@RequestMapping("/api")
public class TimetableController {
    private static final String WEEK = """
            SELECT e.id, e.class_id, e.kind, e.day, e.start_time, e.end_time, e.room, e.title,
                   c.class_code, k.name AS course
            FROM (
                SELECT l.id, 'LESSON' AS kind, CAST(l.lesson_date AS CHAR) AS day,
                       TIME_FORMAT(l.start_time, '%H:%i') AS start_time,
                       TIME_FORMAT(l.end_time, '%H:%i') AS end_time, p.name AS room,
                       '' AS title, l.class_id
                FROM lich_hoc l JOIN phong_hoc p ON p.id = l.room_id
                WHERE l.lesson_date >= ? AND l.lesson_date < ? AND l.status <> 'CANCELLED'
                UNION ALL
                SELECT x.id, 'EXAM', CAST(DATE(x.starts_at) AS CHAR),
                       TIME_FORMAT(x.starts_at, '%H:%i'), TIME_FORMAT(x.ends_at, '%H:%i'),
                       x.room, x.title, x.class_id
                FROM lich_thi x WHERE x.starts_at >= ? AND x.starts_at < ?
            ) e JOIN lop_hoc c ON c.id = e.class_id
                JOIN hoc_phan m ON m.id = c.module_id
                JOIN khoa_hoc k ON k.id = m.course_id
            """;
    private static final String ORDER = " ORDER BY e.day, e.start_time, e.kind, e.id";

    private final JdbcTemplate sql;
    private final CurrentUserService currentUser;

    public TimetableController(JdbcTemplate sql, CurrentUserService currentUser) {
        this.sql = sql;
        this.currentUser = currentUser;
    }

    @GetMapping("/academics/student/timetable")
    public List<Map<String, Object>> student(Principal principal, @RequestParam LocalDate start) {
        return sql.queryForList(WEEK + """
                WHERE EXISTS (SELECT 1 FROM dang_ky_hoc_phan d WHERE d.class_id = c.id
                    AND d.student_id = ? AND d.status IN ('CONFIRMED', 'COMPLETED'))
                """ + ORDER, start, start.plusDays(7), start, start.plusDays(7), currentUser.studentId(principal));
    }

    @GetMapping("/academics/teacher/timetable")
    public List<Map<String, Object>> teacher(Principal principal, @RequestParam LocalDate start) {
        return sql.queryForList(WEEK + " WHERE c.teacher_id = ?" + ORDER,
                start, start.plusDays(7), start, start.plusDays(7), currentUser.employeeId(principal));
    }

    @GetMapping("/training/timetable")
    public List<Map<String, Object>> training(@RequestParam LocalDate start) {
        return sql.queryForList(WEEK + ORDER, start, start.plusDays(7), start, start.plusDays(7));
    }
}
