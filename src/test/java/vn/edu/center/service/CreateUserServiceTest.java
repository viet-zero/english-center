package vn.edu.center.service;

import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import vn.edu.center.entity.Account;
import vn.edu.center.entity.Employee;
import vn.edu.center.entity.Role;
import vn.edu.center.entity.Student;
import vn.edu.center.entity.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreateUserServiceTest {
    private final EntityManager database = mock(EntityManager.class, withSettings().mockMaker("mock-maker-subclass"));
    private final Query permission = mock(Query.class, withSettings().mockMaker("mock-maker-subclass").defaultAnswer(RETURNS_SELF));
    private final Query assignment = mock(Query.class, withSettings().mockMaker("mock-maker-subclass").defaultAnswer(RETURNS_SELF));
    private final CreateUserService service = new CreateUserService(database);
    private final CreateUserService.AccountRequest account =
            new CreateUserService.AccountRequest("Demo User", null, null, null,
                    "demo.user", "example-password");

    @BeforeEach
    void setUp() {
        when(database.createNativeQuery(startsWith("SELECT COUNT(*)"))).thenReturn(permission);
        when(database.createNativeQuery(startsWith("INSERT INTO"))).thenReturn(assignment);
        when(permission.getSingleResult()).thenReturn(1L);
        when(database.find(eq(Role.class), anyString())).thenReturn(new Role());
    }

    @Test
    void createsStudentWithHashedPasswordAndStudentRole() {
        var created = service.createStudent("trainer", new CreateUserService.StudentRequest(account, "HV01"));
        var savedAccount = ArgumentCaptor.forClass(Account.class);
        var savedStudent = ArgumentCaptor.forClass(Student.class);
        verify(database).persist(savedAccount.capture());
        verify(database).persist(savedStudent.capture());
        assertEquals(created.userId(), savedAccount.getValue().getUserId());
        assertEquals(created.userId(), savedStudent.getValue().getUserId());
        assertTrue(new BCryptPasswordEncoder().matches(account.password(),
                savedAccount.getValue().getPasswordHash()));
        verify(permission).setParameter("role", "TRAINING_STAFF");
        verify(assignment).setParameter("accountId", created.accountId());
        verify(assignment).setParameter("role", "STUDENT");
    }

    @Test
    void createsOneEmployeeAccountWithBothRequestedRoles() {
        service.createEmployee("admin", new CreateUserService.EmployeeRequest(account, "NV01", null, null),
                Set.of(CreateUserService.EmployeeRole.TEACHER, CreateUserService.EmployeeRole.ACCOUNTANT));
        verify(database).persist(any(User.class));
        verify(database).persist(any(Account.class));
        verify(database).persist(any(Employee.class));
        verify(permission).setParameter("role", "ADMIN");
        verify(assignment).setParameter("role", "TEACHER");
        verify(assignment).setParameter("role", "ACCOUNTANT");
        verify(assignment, times(2)).executeUpdate();
    }

    @Test
    void unauthorizedActorCannotWriteAnyRecords() {
        when(permission.getSingleResult()).thenReturn(0L);
        var error = assertThrows(ResponseStatusException.class,
                () -> service.createStudent("student", new CreateUserService.StudentRequest(account, "HV01")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(database, never()).persist(any());
        verifyNoInteractions(assignment);
    }

    @Test
    void missingRoleStopsCreationBeforeWritingRecords() {
        when(database.find(Role.class, "ACCOUNTANT")).thenReturn(null);
        assertThrows(IllegalStateException.class,
                () -> service.createEmployee("admin",
                        new CreateUserService.EmployeeRequest(account, "NV01", null, null),
                        Set.of(CreateUserService.EmployeeRole.ACCOUNTANT)));
        verify(database, never()).persist(any());
        verifyNoInteractions(assignment);
    }
}
