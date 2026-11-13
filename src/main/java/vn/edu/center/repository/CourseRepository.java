package vn.edu.center.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.center.entity.Course;

public interface CourseRepository extends JpaRepository<Course, String> {
    List<Course> findAllByStatusOrderByName(String status);

    @Query(value = """
            SELECT c.id, c.class_code AS classCode, u.full_name AS teacherName,
                   c.start_date AS startDate, c.end_date AS endDate, m.tuition,
                   c.max_students - COUNT(e.id) AS remainingSeats
            FROM lop_hoc c
            JOIN hoc_phan m ON m.id = c.module_id
            JOIN khoa_hoc k ON k.id = m.course_id
            JOIN nhan_vien t ON t.id = c.teacher_id
            JOIN nguoi_dung u ON u.id = t.user_id
            LEFT JOIN dang_ky_hoc_phan e ON e.class_id = c.id
                 AND e.status IN ('CONFIRMED', 'COMPLETED')
            WHERE c.module_id = :moduleId AND c.status = 'OPEN'
              AND m.status = 'ACTIVE' AND k.status = 'ACTIVE'
              AND t.status = 'ACTIVE' AND t.deleted = FALSE
            GROUP BY c.id, c.class_code, u.full_name, c.start_date, c.end_date,
                     m.tuition, c.max_students
            ORDER BY c.start_date, c.class_code
            """, nativeQuery = true)
    List<OpenClassRow> findOpenClasses(@Param("moduleId") String moduleId);

    @Query(value = """
            SELECT l.class_id AS classId, l.lesson_date AS lessonDate,
                   l.start_time AS startTime, l.end_time AS endTime, p.name AS roomName
            FROM lich_hoc l
            JOIN phong_hoc p ON p.id = l.room_id
            WHERE l.class_id IN (:classIds) AND l.status <> 'CANCELLED'
            ORDER BY l.lesson_date, l.start_time, l.id
            """, nativeQuery = true)
    List<LessonRow> findLessons(@Param("classIds") List<String> classIds);

    interface OpenClassRow {
        String getId();
        String getClassCode();
        String getTeacherName();
        LocalDate getStartDate();
        LocalDate getEndDate();
        BigDecimal getTuition();
        long getRemainingSeats();
    }

    interface LessonRow {
        String getClassId();
        LocalDate getLessonDate();
        LocalTime getStartTime();
        LocalTime getEndTime();
        String getRoomName();
    }
}
