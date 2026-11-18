package vn.edu.center.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.center.service.*;

@RestController
public class StudentRequestController {
    private final StudentRequestService requests;
    private final CurrentUserService users;
    private final PayOsClient payments;

    public StudentRequestController(StudentRequestService requests, CurrentUserService users, PayOsClient payments) {
        this.requests = requests; this.users = users; this.payments = payments;
    }

    @GetMapping("/api/academics/student/requests")
    public List<Map<String, Object>> student(Principal principal) {
        return requests.studentRequests(users.studentId(principal));
    }

    @GetMapping("/api/academics/student/request-enrollments")
    public List<Map<String, Object>> enrollments(Principal principal) {
        return requests.enrollments(users.studentId(principal));
    }

    @PostMapping("/api/academics/student/requests")
    public Map<String, String> submit(Principal principal, @Valid @RequestBody Submission body) {
        return Map.of("id", requests.submit(users.studentId(principal), body.enrollmentId(), body.kind(), body.reason()));
    }

    @GetMapping("/api/training/student-requests")
    public List<Map<String, Object>> training() { return requests.trainingRequests(); }

    @PutMapping("/api/training/student-requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void review(Principal principal, @PathVariable String id, @Valid @RequestBody Decision body) {
        requests.review(id, users.employeeId(principal), body.approved(), body.note());
    }

    @GetMapping("/api/payments/student-refunds")
    public Map<String, Object> refunds() {
        return Map.of("demo", payments.isDemo(), "items", requests.refunds());
    }

    @PostMapping("/api/payments/student-refunds/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refund(Principal principal, @PathVariable String id, @Valid @RequestBody Refund body) {
        if (!payments.isDemo()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        requests.refund(id, users.employeeId(principal), body.note(),body.method()==null ? "BANK_TRANSFER" : body.method());
    }

    public record Submission(@NotBlank @Size(max=36) String enrollmentId,
            @NotBlank @Pattern(regexp="CANCEL|HOLD") String kind,
            @NotBlank @Size(max=1000) String reason) {}
    public record Decision(@NotNull Boolean approved, @NotBlank @Size(max=1000) String note) {}
    public record Refund(@NotBlank @Size(max=1000) String note,
            @Pattern(regexp="CASH|BANK_TRANSFER") String method) {}
}
