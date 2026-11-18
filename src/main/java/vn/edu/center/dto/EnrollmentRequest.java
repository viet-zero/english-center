package vn.edu.center.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnrollmentRequest(@NotBlank @Size(max = 36) String classId,
        @Size(max=30) @jakarta.validation.constraints.Pattern(regexp="^$|[A-Za-z0-9_-]{3,30}") String couponCode) {}