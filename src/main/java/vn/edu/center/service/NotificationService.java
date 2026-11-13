package vn.edu.center.service;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final JdbcTemplate sql;

    public NotificationService(JdbcTemplate sql) {
        this.sql = sql;
    }

    public void notifyClass(String classId, String message) {
        sql.update("""
                INSERT INTO thong_bao (student_id, message)
                SELECT student_id, ? FROM dang_ky_hoc_phan
                WHERE class_id = ? AND status IN ('CONFIRMED', 'COMPLETED')
                """, message, classId);
    }

    public void notifyStudent(String studentId, String message) {
        sql.update("INSERT INTO thong_bao (student_id, message) VALUES (?, ?)", studentId, message);
    }

    public Inbox inbox(String studentId) {
        var unread = sql.queryForObject(
                "SELECT COUNT(*) FROM thong_bao WHERE student_id = ? AND read_at IS NULL", Long.class, studentId);
        var items = sql.queryForList("""
                SELECT id, message, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created_at,
                       read_at IS NOT NULL AS is_read
                FROM thong_bao WHERE student_id = ?
                ORDER BY (read_at IS NULL) DESC, id DESC LIMIT 30
                """, studentId);
        return new Inbox(unread, items);
    }

    public void markRead(String studentId, long id) {
        sql.update("""
                UPDATE thong_bao SET read_at = CURRENT_TIMESTAMP
                WHERE id = ? AND student_id = ? AND read_at IS NULL
                """, id, studentId);
    }

    public record Inbox(long unread, List<Map<String, Object>> items) {}
}
