package vn.edu.center.controller;

import java.util.List;
import java.util.Map;

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
                SELECT x.id, x.title, c.class_code, x.room, x.exam_format, x.invigilator_id,
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
        if (details.invigilatorId() != null && sql.queryForList(
                "SELECT n.id " + INVIGILATORS + " AND n.id = ?",
                String.class, details.invigilatorId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giám thị phải là giảng viên đang hoạt động.");
        }
        int changed = sql.update("""
                UPDATE lich_thi SET room = ?, exam_format = ?, invigilator_id = ? WHERE id = ?
                """, details.room().strip(), details.format().name(), details.invigilatorId(), examId);
        if (changed == 0 && sql.queryForList("SELECT id FROM lich_thi WHERE id = ?", String.class, examId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ thi.");
        }
    }

    public enum Format { ONSITE, ONLINE }

    public record ExamDetails(@NotBlank @Size(max = 100) String room, @NotNull Format format,
            @Size(max = 36) String invigilatorId) {}
}
