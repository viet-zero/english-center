package vn.edu.center.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.center.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, String> {
    @Query(value = """
            SELECT COUNT(*) FROM nhan_vien e
            JOIN tai_khoan a ON a.user_id = e.user_id
            JOIN tai_khoan_vai_tro r ON r.account_id = a.id
            WHERE e.id = :employeeId AND e.status = 'ACTIVE' AND e.deleted = FALSE
              AND a.status = 'ACTIVE' AND r.role_id = 'TEACHER'
            """, nativeQuery = true)
    long countActiveTeacher(@Param("employeeId") String employeeId);
}
