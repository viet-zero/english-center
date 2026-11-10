package vn.edu.center.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.center.entity.Module;

public interface ModuleRepository extends JpaRepository<Module, String> {
}