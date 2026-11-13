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

    public InvoiceService(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    public InvoiceResponse findForStudent(String invoiceId, String studentId) {
        var invoice = invoices.findById(invoiceId)
                .filter(value -> studentId.equals(value.getStudentId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        return new InvoiceResponse(invoice.getId(), invoice.getCreatedAt(),
                invoice.getTotalDue(), invoice.getTotalPaid(),
                invoice.getTotalDue().subtract(invoice.getTotalPaid()), invoice.getStatus());
    }

    public record InvoiceResponse(String id, LocalDateTime createdAt, BigDecimal totalDue,
            BigDecimal totalPaid, BigDecimal remainingAmount, String status) {}
}