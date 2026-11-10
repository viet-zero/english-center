package vn.edu.center.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.center.entity.Enrollment;

public interface EnrollmentRepository extends JpaRepository<Enrollment, String> {
    Optional<Enrollment> findByStudentIdAndClassId(String studentId, String classId);

    @Query("select count(e) from Enrollment e where e.classId = :classId "
         + "and e.status in ('CONFIRMED', 'COMPLETED')")
    long countOccupiedSeats(@Param("classId") String classId);

    @Query("select count(e) from Enrollment e, Classroom c "
         + "where e.classId = c.id and e.studentId = :studentId "
         + "and c.moduleId = :moduleId and e.status in ('CONFIRMED', 'COMPLETED')")
    long countActiveModule(@Param("studentId") String studentId, @Param("moduleId") String moduleId);

    @Query(value = "SELECT COUNT(*) FROM dang_ky_hoc_phan dk "
        + "JOIN lop_hoc lh ON lh.id = dk.class_id "
        + "JOIN hoc_phan hp ON hp.id = lh.module_id "
        + "WHERE dk.student_id = :studentId AND dk.status = 'COMPLETED' "
        + "AND hp.course_id = :courseId AND hp.sequence_number = :previousSequence",
        nativeQuery = true)
    long countCompletedPrerequisite(@Param("studentId") String studentId,
        @Param("courseId") String courseId, @Param("previousSequence") int previousSequence);

    @Query(value = "SELECT COUNT(*) FROM lich_hoc target "
        + "JOIN lich_hoc current_lesson ON current_lesson.lesson_date = target.lesson_date "
        + "AND current_lesson.start_time < target.end_time "
        + "AND current_lesson.end_time > target.start_time "
        + "JOIN dang_ky_hoc_phan dk ON dk.class_id = current_lesson.class_id "
        + "WHERE target.class_id = :classId AND dk.student_id = :studentId "
        + "AND target.status <> 'CANCELLED' AND current_lesson.status <> 'CANCELLED' "
        + "AND dk.status IN ('CONFIRMED', 'COMPLETED')", nativeQuery = true)
    long countScheduleConflicts(@Param("studentId") String studentId, @Param("classId") String classId);
}