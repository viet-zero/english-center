package vn.edu.center.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.center.entity.Module;

public interface ModuleRepository extends JpaRepository<Module, String> {
    List<Module> findAllByCourseIdAndStatusOrderBySequenceNumber(String courseId, String status);
    List<Module> findAllByCourseIdOrderBySequenceNumber(String courseId);
}