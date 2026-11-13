package vn.edu.center.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.constraints.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.center.entity.Classroom;
import vn.edu.center.entity.Course;
import vn.edu.center.entity.Module;
import vn.edu.center.repository.*;

@Service
@Transactional(readOnly = true)
public class CourseService {
    private final CourseRepository courses;
    private final ModuleRepository modules;
    private final ClassroomRepository classes;
    private final EmployeeRepository employees;
    private final EnrollmentRepository enrollments;

    public CourseService(CourseRepository courses, ModuleRepository modules,
            ClassroomRepository classes, EmployeeRepository employees, EnrollmentRepository enrollments) {
        this.courses = courses;
        this.modules = modules;
        this.classes = classes;
        this.employees = employees;
        this.enrollments = enrollments;
    }

    public List<Course> listCourses(boolean includeInactive) {
        return includeInactive ? courses.findAll(Sort.by("name")) : courses.findAllByStatusOrderByName("ACTIVE");
    }

    public List<Module> listModules(String courseId, boolean includeInactive) {
        Course course = findCourse(courseId);
        if (includeInactive) {
            return modules.findAllByCourseIdOrderBySequenceNumber(courseId);
        }
        if (!"ACTIVE".equals(course.getStatus())) {
            throw notFound("Course");
        }
        return modules.findAllByCourseIdAndStatusOrderBySequenceNumber(courseId, "ACTIVE");
    }

    public List<Classroom> listManagedClasses(String moduleId) {
        findModule(moduleId);
        return classes.findAllByModuleIdOrderByStartDateAscClassCodeAsc(moduleId);
    }

    public List<OpenClass> listOpenClasses(String moduleId) {
        var rows = courses.findOpenClasses(moduleId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, List<Lesson>> lessons = courses.findLessons(rows.stream().map(CourseRepository.OpenClassRow::getId).toList())
                .stream().collect(Collectors.groupingBy(CourseRepository.LessonRow::getClassId,
                        Collectors.mapping(row -> new Lesson(row.getLessonDate(), row.getStartTime(),
                                row.getEndTime(), row.getRoomName()), Collectors.toList())));
        return rows.stream().map(row -> new OpenClass(row.getId(), row.getClassCode(), row.getTeacherName(),
                row.getStartDate(), row.getEndDate(), row.getTuition(), Math.max(0, row.getRemainingSeats()),
                lessons.getOrDefault(row.getId(), List.of()))).toList();
    }

    @Transactional
    public Course createCourse(CourseDetails request) {
        Course course = new Course();
        course.setId(newId());
        return saveCourse(course, request);
    }

    @Transactional
    public Course updateCourse(String courseId, CourseDetails request) {
        return saveCourse(findCourse(courseId), request);
    }

    private Course saveCourse(Course course, CourseDetails request) {
        course.setName(request.name().strip());
        course.setDescription(request.description());
        course.setStatus(request.status().name());
        return courses.saveAndFlush(course);
    }

    @Transactional
    public Module createModule(String courseId, ModuleRequest request) {
        findCourse(courseId);
        Module module = new Module();
        module.setId(newId());
        module.setCourseId(courseId);
        module.setSequenceNumber(request.sequenceNumber());
        return saveModule(module, request.details());
    }

    @Transactional
    public Module updateModule(String moduleId, ModuleDetails request) {
        return saveModule(findModule(moduleId), request);
    }

    private Module saveModule(Module module, ModuleDetails request) {
        module.setName(request.name().strip());
        module.setTuition(request.tuition());
        module.setDurationHours(request.durationHours());
        module.setStatus(request.status().name());
        return modules.saveAndFlush(module);
    }

    @Transactional
    public Classroom createClass(String moduleId, ClassRequest request) {
        findModule(moduleId);
        if (employees.countActiveTeacher(request.teacherId()) == 0) {
            throw new IllegalStateException("An active teacher is required");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("End date must not precede start date");
        }
        Classroom classroom = new Classroom();
        classroom.setId(newId());
        classroom.setModuleId(moduleId);
        classroom.setTeacherId(request.teacherId());
        classroom.setClassCode(request.classCode().strip());
        classroom.setStartDate(request.startDate());
        classroom.setEndDate(request.endDate());
        return saveClass(classroom, request.details());
    }

    @Transactional
    public Classroom updateClass(String classId, ClassDetails request) {
        Classroom classroom = classes.findLockedById(classId).orElseThrow(() -> notFound("Class"));
        if (enrollments.countOccupiedSeats(classId) > request.maxStudents()) {
            throw new IllegalStateException("Capacity cannot be lower than occupied seats");
        }
        return saveClass(classroom, request);
    }

    private Classroom saveClass(Classroom classroom, ClassDetails request) {
        classroom.setMaxStudents(request.maxStudents());
        classroom.setStatus(request.status().name());
        return classes.saveAndFlush(classroom);
    }

    private Course findCourse(String id) {
        return courses.findById(id).orElseThrow(() -> notFound("Course"));
    }

    private Module findModule(String id) {
        return modules.findById(id).orElseThrow(() -> notFound("Module"));
    }

    private static ResponseStatusException notFound(String entity) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, entity + " not found");
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    public enum CatalogStatus { ACTIVE, INACTIVE }
    public enum ClassStatus { OPEN, IN_PROGRESS, CLOSED }

    public record CourseDetails(@NotBlank @Size(max = 150) String name,
            @Size(max = 5000) String description, @NotNull CatalogStatus status) {}

    public record ModuleDetails(@NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal tuition,
            @NotNull @Positive Integer durationHours, @NotNull CatalogStatus status) {}

    public record ModuleRequest(@NotNull @Positive Integer sequenceNumber,
            @NotNull @jakarta.validation.Valid ModuleDetails details) {}

    public record ClassDetails(@NotNull @Positive Integer maxStudents, @NotNull ClassStatus status) {}

    public record ClassRequest(@NotBlank @Size(max = 36) String teacherId,
            @NotBlank @Size(max = 30) String classCode, @NotNull LocalDate startDate,
            @NotNull LocalDate endDate, @NotNull @jakarta.validation.Valid ClassDetails details) {}

    public record OpenClass(String id, String classCode, String teacherName, LocalDate startDate,
            LocalDate endDate, BigDecimal tuition, long remainingSeats, List<Lesson> lessons) {}

    public record Lesson(LocalDate date, LocalTime startTime, LocalTime endTime, String roomName) {}
}
