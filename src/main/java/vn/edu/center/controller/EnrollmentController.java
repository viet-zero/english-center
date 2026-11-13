package vn.edu.center.controller;

import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.edu.center.dto.EnrollmentRequest;
import vn.edu.center.service.CurrentUserService;
import vn.edu.center.service.EnrollmentService;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {
    private final EnrollmentService enrollments;
    private final CurrentUserService currentUser;

    public EnrollmentController(EnrollmentService enrollments, CurrentUserService currentUser) {
        this.enrollments = enrollments;
        this.currentUser = currentUser;
    }

    @PostMapping
    public EnrollmentService.RegistrationResult register(
            Principal principal, @Valid @RequestBody EnrollmentRequest request) {
        return enrollments.register(currentUser.studentId(principal), request.classId());
    }
}