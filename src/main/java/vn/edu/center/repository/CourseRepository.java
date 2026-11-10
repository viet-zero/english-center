package vn.edu.center.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.center.entity.Course;

public interface CourseRepository extends JpaRepository<Course, String> {
}