package vn.edu.center.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.center.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, String> {
}