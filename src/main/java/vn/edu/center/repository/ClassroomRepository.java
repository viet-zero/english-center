package vn.edu.center.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.center.entity.Classroom;

public interface ClassroomRepository extends JpaRepository<Classroom, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Classroom e where e.id = :id")
    Optional<Classroom> findLockedById(@Param("id") String id);
}