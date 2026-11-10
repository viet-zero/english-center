package vn.edu.center.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.center.entity.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Invoice e where e.id = :id")
    Optional<Invoice> findLockedById(@Param("id") String id);
}