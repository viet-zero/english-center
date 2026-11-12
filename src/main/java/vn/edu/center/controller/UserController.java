package vn.edu.center.controller;

import java.security.Principal;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.center.service.CreateUserService;
import vn.edu.center.service.CreateUserService.CreatedUser;
import vn.edu.center.service.CreateUserService.EmployeeRequest;
import vn.edu.center.service.CreateUserService.EmployeeRole;
import vn.edu.center.service.CreateUserService.StudentRequest;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final CreateUserService users;

    public UserController(CreateUserService users) {
        this.users = users;
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedUser createStudent(Principal principal, @Valid @RequestBody StudentRequest request) {
        return users.createStudent(principal.getName(), request);
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedUser createEmployee(Principal principal, @Valid @RequestBody CreateEmployeeBody body) {
        return users.createEmployee(principal.getName(), body.employee(), body.roles());
    }

    public record CreateEmployeeBody(@NotNull @Valid EmployeeRequest employee,
            @NotEmpty Set<@NotNull EmployeeRole> roles) {}
}
