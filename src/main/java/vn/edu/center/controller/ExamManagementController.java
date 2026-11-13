package vn.edu.center.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/training/exams")
public class ExamManagementController {
    private static final String INVIGILATORS = """
            FROM nhan_vien n JOIN nguoi_dung u ON u.id = n.user_id
            JOIN tai_khoan a ON a.user_id = n.user_id
            JOIN tai_khoan_vai_tro r ON r.account_id = a.id
            WHERE n.status = 'ACTIVE' AND n.deleted = FALSE
              AND a.status = 'ACTIVE' AND r.role_id = 'TEACHER'
            """;

    private final JdbcTemplate sql;

    public ExamManagementController(JdbcTemplate sql) {
        this.sql = sql;
    }

    @GetMapping
    public List<Map<String, Object>> exams() {
        return sql.queryForList("""
                SELECT x.id, x.title, c.class_code, x.room, x.exam_format, (SELECT GROUP_CONCAT(g.employee_id ORDER BY g.employee_id)
                        FROM giam_thi_ky_thi g WHERE g.exam_id = x.id) AS invigilator_ids,
                       DATE_FORMAT(x.starts_at, '%Y-%m-%d %H:%i') AS starts_at,
                       DATE_FORMAT(x.ends_at, '%Y-%m-%d %H:%i') AS ends_at
                FROM lich_thi x JOIN lop_hoc c ON c.id = x.class_id
                ORDER BY x.starts_at, x.id
                """);
    }

    @GetMapping("/invigilators")
    public List<Map<String, Object>> invigilators() {
        return sql.queryForList("SELECT n.id, n.employee_code, u.full_name " + INVIGILATORS
                + " ORDER BY u.full_name, n.id");
    }

    @PutMapping("/{examId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void update(@PathVariable String examId, @Valid @RequestBody ExamDetails details) {
        if (sql.queryForList("SELECT id FROM lich_thi WHERE id = ? FOR UPDATE", String.class, examId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ thi.");
        }
        for (String employeeId : details.invigilatorIds()) {
            if (sql.queryForList(
                "SELECT n.id " + INVIGILATORS + " AND n.id = ?",
                String.class, employeeId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giám thị phải là giảng viên đang hoạt động.");
            }
        }
        sql.update("UPDATE lich_thi SET room = ?, exam_format = ? WHERE id = ?",
                details.room().strip(), details.format().name(), examId);
        sql.update("DELETE FROM giam_thi_ky_thi WHERE exam_id = ?", examId);
        sql.batchUpdate("INSERT INTO giam_thi_ky_thi (exam_id, employee_id) VALUES (?, ?)",
                details.invigilatorIds().stream().map(id -> new Object[] {examId, id}).toList());
    }

    public enum Format { ONSITE, ONLINE }

    public record ExamDetails(@NotBlank @Size(max = 100) String room, @NotNull Format format,
            @NotNull @Size(max = 30) Set<@NotBlank @Size(max = 36) String> invigilatorIds) {}
}
