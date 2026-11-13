package vn.edu.center.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import vn.edu.center.entity.Account;
import vn.edu.center.entity.Employee;
import vn.edu.center.entity.Role;
import vn.edu.center.entity.Student;
import vn.edu.center.entity.User;

@Service
@Validated
public class CreateUserService {
    private final EntityManager database;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public CreateUserService(EntityManager database) {
        this.database = database;
    }

    // Pass the authenticated username from the server, never from the request body.
    @Transactional
    public CreatedUser createStudent(String actorUsername, @NotNull @Valid StudentRequest request) {
        requireRole(actorUsername, "ADMIN");
        requireExistingRole("STUDENT");

        Account account = createUserAndAccount(request.account());
        Student student = new Student();
        student.setId(UUID.randomUUID().toString());
        student.setUserId(account.getUserId());
        student.setStudentCode(request.studentCode());
        student.setEnrollmentDate(LocalDate.now());
        student.setStatus("ACTIVE");
        database.persist(student);
        database.flush();
        assignRole(account.getId(), "STUDENT");
        return new CreatedUser(account.getUserId(), account.getId(), student.getId());
    }

    @Transactional
    public CreatedUser createEmployee(String actorUsername, @NotNull @Valid EmployeeRequest request,
            @NotEmpty Set<@NotNull EmployeeRole> roles) {
        requireRole(actorUsername, "ADMIN");
        for (EmployeeRole role : roles) {
            requireExistingRole(role.name());
        }

        Account account = createUserAndAccount(request.account());
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID().toString());
        employee.setUserId(account.getUserId());
        employee.setEmployeeCode(request.employeeCode());
        employee.setPosition(request.position());
        employee.setSpecialization(request.specialization());
        employee.setHireDate(LocalDate.now());
        employee.setStatus("ACTIVE");
        database.persist(employee);
        database.flush();
        for (EmployeeRole role : roles) {
            assignRole(account.getId(), role.name());
        }
        return new CreatedUser(account.getUserId(), account.getId(), employee.getId());
    }

    private Account createUserAndAccount(AccountRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password exceeds 72 UTF-8 bytes");
        }

        // Database unique constraints reject duplicate username, phone, email and profile code,
        // including requests that arrive concurrently.
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setFullName(request.fullName().strip());
        user.setPhone(blankToNull(request.phone()));
        user.setEmail(blankToNull(request.email()));
        user.setIdentityNumber(blankToNull(request.identityNumber()));
        database.persist(user);

        Account account = new Account();
        account.setId(UUID.randomUUID().toString());
        account.setUserId(user.getId());
        account.setUsername(request.username().strip());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setStatus("ACTIVE");
        database.persist(account);
        return account;
    }

    private void requireRole(String username, String role) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Number matches = (Number) database.createNativeQuery("""
                SELECT COUNT(*)
                FROM tai_khoan a
                JOIN tai_khoan_vai_tro r ON r.account_id = a.id
                WHERE a.username = :username AND a.status = 'ACTIVE' AND r.role_id = :role
                """)
                .setParameter("username", username)
                .setParameter("role", role)
                .getSingleResult();
        if (matches.longValue() == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Required role is missing");
        }
    }

    private void requireExistingRole(String role) {
        if (database.find(Role.class, role) == null) {
            throw new IllegalStateException("Role has not been configured: " + role);
        }
    }

    private void assignRole(String accountId, String role) {
        database.createNativeQuery("""
                INSERT INTO tai_khoan_vai_tro (account_id, role_id)
                VALUES (:accountId, :role)
                """)
                .setParameter("accountId", accountId)
                .setParameter("role", role)
                .executeUpdate();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public enum EmployeeRole {
        ADMIN, TRAINING_STAFF, ACCOUNTANT, TEACHER
    }

    public record AccountRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 20) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 20) String identityNumber,
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(min = 8, max = 72) String password) {
        @Override
        public String toString() {
            return "AccountRequest[username=" + username + "]";
        }
    }

    public record StudentRequest(
            @NotNull @Valid AccountRequest account,
            @NotBlank @Size(max = 30) String studentCode) {}

    public record EmployeeRequest(
            @NotNull @Valid AccountRequest account,
            @NotBlank @Size(max = 30) String employeeCode,
            @Size(max = 100) String position,
            @Size(max = 255) String specialization) {}

    public record CreatedUser(String userId, String accountId, String profileId) {}
}
