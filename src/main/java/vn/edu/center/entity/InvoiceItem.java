package vn.edu.center.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "chi_tiet_hoa_don", uniqueConstraints = {@jakarta.persistence.UniqueConstraint(columnNames = "enrollment_id"), @jakarta.persistence.UniqueConstraint(columnNames = "invoice_id")})
public class InvoiceItem {

    @Id
    private String id;

    private String invoiceId;

    private String enrollmentId;

    private BigDecimal amount;

    private BigDecimal discount;

    private BigDecimal total;

    public InvoiceItem() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(String enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }
}
