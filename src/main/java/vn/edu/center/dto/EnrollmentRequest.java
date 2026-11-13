package vn.edu.center.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnrollmentRequest(@NotBlank @Size(max = 36) String classId) {}