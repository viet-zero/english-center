package vn.edu.center.service;

import java.security.Principal;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CurrentUserService {
    private final EntityManager entityManager;

    public CurrentUserService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public String studentId(Principal principal) {
        return resolve(principal, "Student");
    }

    public String employeeId(Principal principal) {
        return resolve(principal, "Employee");
    }

    private String resolve(Principal principal, String entity) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        // Entity is selected only by the two methods above, never from request input.
        List<String> ids = entityManager.createQuery(
                "select p.id from " + entity + " p, Account a "
                + "where p.userId = a.userId and a.username = :username "
                + "and a.status = 'ACTIVE' and p.status = 'ACTIVE'"
                + ("Employee".equals(entity) ? " and p.deleted = false" : ""), String.class)
                .setParameter("username", principal.getName()).setMaxResults(2).getResultList();
        if (ids.size() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active user profile required");
        }
        return ids.get(0);
    }
}
