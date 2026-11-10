package vn.edu.center.repository;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.center.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByReferenceCode(String referenceCode);

    @Query("select coalesce(sum(p.amount), 0) from Payment p "
         + "where p.invoiceId = :invoiceId and p.status = 'SUCCESS'")
    BigDecimal sumSuccessfulPayments(@Param("invoiceId") String invoiceId);

    @Query(value = "SELECT COUNT(*) FROM tai_khoan tk "
        + "JOIN tai_khoan_vai_tro tr ON tr.account_id = tk.id "
        + "JOIN nhan_vien nv ON nv.user_id = tk.user_id "
        + "WHERE nv.id = :employeeId AND nv.status = 'ACTIVE' "
        + "AND nv.deleted = FALSE AND tk.status = 'ACTIVE' AND tr.role_id = 'ACCOUNTANT'",
        nativeQuery = true)
    long countAccountantRole(@Param("employeeId") String employeeId);
}