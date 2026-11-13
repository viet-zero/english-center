package vn.edu.center.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.center.entity.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    @Query(value = "SELECT id FROM hoa_don WHERE invoice_number = :number", nativeQuery = true)
    Optional<String> findIdByNumber(@Param("number") long number);

    @Query(value = "SELECT invoice_number FROM hoa_don WHERE id = :id", nativeQuery = true)
    long findNumberById(@Param("id") String id);

    default String resolveId(String reference) {
        if (reference.matches("(?i)HD[0-9]{1,18}")) {
            return findIdByNumber(Long.parseLong(reference.substring(2))).orElse(reference);
        }
        return reference;
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Invoice e where e.id = :id")
    Optional<Invoice> findLockedById(@Param("id") String id);
}
