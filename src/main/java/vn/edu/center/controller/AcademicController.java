package vn.edu.center.controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.center.service.AcademicService;
import vn.edu.center.service.CurrentUserService;

@RestController
@RequestMapping("/api/academics")
public class AcademicController {
    private final AcademicService academics;
    private final CurrentUserService currentUser;

    public AcademicController(AcademicService academics, CurrentUserService currentUser) {
        this.academics = academics;
        this.currentUser = currentUser;
    }

    @GetMapping("/teacher/classes")
    public List<Map<String, Object>> teacherClasses(Principal principal) {
        return academics.teacherClasses(currentUser.employeeId(principal));
    }

    @GetMapping("/student/classes")
    public List<Map<String, Object>> studentClasses(Principal principal) {
        return academics.studentClasses(currentUser.studentId(principal));
    }

    @GetMapping("/teacher/classes/{classId}")
    public Map<String, Object> teacherClass(Principal principal, @PathVariable String classId) {
        return academics.teacherView(currentUser.employeeId(principal), classId);
    }

    @GetMapping("/student/classes/{classId}")
    public Map<String, Object> studentClass(Principal principal, @PathVariable String classId) {
        return academics.studentView(currentUser.studentId(principal), classId);
    }

    @PostMapping("/teacher/classes/{classId}/exams")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> createExam(Principal principal, @PathVariable String classId,
            @Valid @RequestBody ExamRequest body) {
        return Map.of("id", academics.createExam(currentUser.employeeId(principal), classId,
                body.title(), body.startsAt(), body.endsAt(), body.room()));
    }

    @PutMapping("/teacher/classes/{classId}/lessons/{lessonId}/attendance/{enrollmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void attendance(Principal principal, @PathVariable String classId, @PathVariable String lessonId,
            @PathVariable String enrollmentId, @Valid @RequestBody AttendanceRequest body) {
        academics.markAttendance(currentUser.employeeId(principal), classId, lessonId, enrollmentId, body.status().name());
    }

    @PutMapping("/teacher/classes/{classId}/exams/{examId}/grades/{enrollmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grade(Principal principal, @PathVariable String classId, @PathVariable String examId,
            @PathVariable String enrollmentId, @Valid @RequestBody GradeRequest body) {
        academics.grade(currentUser.employeeId(principal), classId, examId, enrollmentId, body.score());
    }

    public record ExamRequest(@NotBlank @Size(max = 150) String title, @NotNull LocalDateTime startsAt,
            @NotNull LocalDateTime endsAt, @NotBlank @Size(max = 100) String room) {}
    public record AttendanceRequest(@NotNull AttendanceStatus status) {}
    public enum AttendanceStatus { PRESENT, ABSENT, LATE, EXCUSED }
    public record GradeRequest(@NotNull @DecimalMin("0") @DecimalMax("10")
            @Digits(integer = 2, fraction = 2) BigDecimal score) {}
}
