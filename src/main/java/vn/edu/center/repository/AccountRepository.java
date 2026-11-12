package vn.edu.center.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.center.entity.Account;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByUsername(String username);

    @Query(value = """
            SELECT r.id
            FROM tai_khoan_vai_tro ar
            JOIN vai_tro r ON r.id = ar.role_id
            WHERE ar.account_id = :accountId
            """, nativeQuery = true)
    List<String> findRoleIds(@Param("accountId") String accountId);
}
