package vn.edu.center.service;

import java.time.LocalDate;
import java.time.LocalTime;
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
public class RoomSchedulingService {
    private static final String OVERLAP = """
            FROM lich_hoc l JOIN lop_hoc c ON c.id = l.class_id
            WHERE l.lesson_date = ? AND l.start_time < ? AND l.end_time > ?
              AND l.status <> 'CANCELLED' AND c.status <> 'CANCELLED'
            """;

    private final JdbcTemplate sql;

    public RoomSchedulingService(JdbcTemplate sql) {
        this.sql = sql;
    }

    public List<Map<String, Object>> classes() {
        return sql.queryForList("""
                SELECT id, class_code, max_students,
                       CAST(start_date AS CHAR) AS start_date, CAST(end_date AS CHAR) AS end_date
                FROM lop_hoc WHERE status IN ('OPEN', 'IN_PROGRESS') ORDER BY class_code
                """);
    }

    public List<Map<String, Object>> rooms(LocalDate day, Shift shift) {
        return sql.queryForList("""
                SELECT p.id, p.name, p.capacity, p.status,
                       EXISTS (SELECT 1
                """ + OVERLAP + " AND l.room_id = p.id) AS occupied FROM phong_hoc p ORDER BY p.name, p.id",
                day, shift.end, shift.start);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String schedule(String classId, String roomId, LocalDate day, Shift shift) {
        // Serialize writes that share a class, teacher or room. Check conflicts after locking.
        Map<String, Object> classroom = sql.queryForList("""
                SELECT teacher_id, max_students, start_date, end_date, status
                FROM lop_hoc WHERE id = ? FOR UPDATE
                """, classId).stream().findFirst().orElseThrow(() -> missing("Class"));
        String teacherId = (String) classroom.get("teacher_id");
        sql.queryForList("SELECT id FROM nhan_vien WHERE id = ? FOR UPDATE", String.class, teacherId);
        Map<String, Object> room = sql.queryForList(
                "SELECT capacity, status FROM phong_hoc WHERE id = ? FOR UPDATE", roomId)
                .stream().findFirst().orElseThrow(() -> missing("Room"));

        if (!List.of("OPEN", "IN_PROGRESS").contains(classroom.get("status"))
                || day.isBefore(LocalDate.parse(classroom.get("start_date").toString()))
                || day.isAfter(LocalDate.parse(classroom.get("end_date").toString()))) {
            throw new IllegalStateException("Lớp không hoạt động trong ngày đã chọn.");
        }
        if (!"AVAILABLE".equals(room.get("status"))) {
            throw new IllegalStateException("Phòng đang bảo trì.");
        }
        if (((Number) room.get("capacity")).intValue() < ((Number) classroom.get("max_students")).intValue()) {
            throw new IllegalStateException("Phòng không đủ sức chứa.");
        }
        int conflicts = sql.queryForObject("SELECT COUNT(*) " + OVERLAP
                + " AND (l.room_id = ? OR l.class_id = ? OR c.teacher_id = ?)", Integer.class,
                day, shift.end, shift.start, roomId, classId, teacherId);
        if (conflicts > 0) {
            throw new IllegalStateException("Phòng, lớp hoặc giảng viên đã có lịch trong ca này.");
        }
        String id = UUID.randomUUID().toString();
        sql.update("""
                INSERT INTO lich_hoc (id, class_id, room_id, lesson_date, start_time, end_time)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, classId, roomId, day, shift.start, shift.end);
        return id;
    }

    private static ResponseStatusException missing(String name) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, name + " not found");
    }

    public enum Shift {
        MORNING("08:30", "11:30"), AFTERNOON("13:30", "16:30"), EVENING("18:00", "20:00");

        private final LocalTime start;
        private final LocalTime end;

        Shift(String start, String end) {
            this.start = LocalTime.parse(start);
            this.end = LocalTime.parse(end);
        }
    }
}
