package vn.edu.center.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.center.entity.InvoiceItem;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, String> {
    Optional<InvoiceItem> findByEnrollmentId(String enrollmentId);
}