package vn.edu.center.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.center.repository.InvoiceRepository;

@Service
@Transactional(readOnly = true)
public class InvoiceService {
    private final InvoiceRepository invoices;
    private final org.springframework.jdbc.core.JdbcTemplate sql;

    public InvoiceService(InvoiceRepository invoices, org.springframework.jdbc.core.JdbcTemplate sql) {
        this.invoices = invoices;
        this.sql = sql;
    }

    public InvoiceResponse findForStudent(String invoiceId, String studentId) {
        var invoice = invoices.findById(invoices.resolveId(invoiceId))
                .filter(value -> studentId.equals(value.getStudentId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        return new InvoiceResponse(invoice.getId(), "HD" + invoices.findNumberById(invoice.getId()), invoice.getCreatedAt(),
                invoice.getTotalDue(), invoice.getTotalPaid(),
                invoice.getTotalDue().subtract(invoice.getTotalPaid()), invoice.getStatus(),
                sql.queryForObject("SELECT COALESCE(SUM(amount),0) FROM hoan_tien_demo WHERE invoice_id=?",
                        BigDecimal.class, invoice.getId()),
                sql.queryForObject("""
                        SELECT COALESCE(SUM(r.remaining_value),0) FROM yeu_cau_hoc_vien r
                        JOIN chi_tiet_hoa_don d ON d.enrollment_id=r.enrollment_id
                        WHERE d.invoice_id=? AND r.kind='HOLD' AND r.status='APPROVED'
                        """, BigDecimal.class, invoice.getId()),
                invoice.getTotalDue().add(invoice.getDiscount()),invoice.getDiscount(),
                invoice.getVoucherId()==null ? null : sql.queryForObject("SELECT code FROM ma_giam_gia WHERE id=?",String.class,invoice.getVoucherId()));
    }

    public String resolveId(String reference) {
        return invoices.resolveId(reference);
    }

    public record InvoiceResponse(String id, String invoiceCode, LocalDateTime createdAt, BigDecimal totalDue,
            BigDecimal totalPaid, BigDecimal remainingAmount, String status, BigDecimal refundedAmount, BigDecimal reservedAmount, BigDecimal tuitionAmount, BigDecimal discount, String couponCode) {}
}
