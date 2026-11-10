package vn.edu.center.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.center.entity.Invoice;
import vn.edu.center.entity.Payment;
import vn.edu.center.repository.InvoiceRepository;
import vn.edu.center.repository.PaymentRepository;

@Service
public class PaymentService {
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;

    public PaymentService(InvoiceRepository invoices, PaymentRepository payments) {
        this.invoices = invoices;
        this.payments = payments;
    }

    // employeeId must be derived from authentication; this operation records confirmed receipts.
    // A retry must reuse referenceCode. Lock failures require retrying the whole transaction.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Payment recordPayment(String invoiceId, String employeeId, BigDecimal amount,
            String method, String referenceCode) {
        if (amount == null || amount.signum() <= 0
                || referenceCode == null || referenceCode.isBlank() || referenceCode.length() > 100
                || !Set.of("CASH", "BANK_TRANSFER").contains(method == null ? "" : method)) {
            throw new IllegalArgumentException("Invalid payment details");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.precision() > 15) {
            throw new IllegalArgumentException("Amount exceeds supported precision");
        }
        Invoice invoice = invoices.findLockedById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        if (payments.countAccountantRole(employeeId) == 0) {
            throw new IllegalStateException("Active accountant role is required");
        }
        Payment previous = payments.findByReferenceCode(referenceCode).orElse(null);
        if (previous != null) {
            if (!invoiceId.equals(previous.getInvoiceId())
                    || !employeeId.equals(previous.getCollectedBy())
                    || normalized.compareTo(previous.getAmount()) != 0
                    || !method.equals(previous.getMethod()) || !"SUCCESS".equals(previous.getStatus())) {
                throw new IllegalStateException("Reference code was already used for another payment");
            }
            return previous;
        }
        BigDecimal collected = payments.sumSuccessfulPayments(invoiceId);
        BigDecimal updatedTotal = collected.add(normalized);
        if (updatedTotal.compareTo(invoice.getTotalDue()) > 0) {
            throw new IllegalStateException("Payment exceeds remaining tuition");
        }
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setInvoiceId(invoiceId);
        payment.setCollectedBy(employeeId);
        payment.setAmount(normalized);
        payment.setPaidAt(LocalDateTime.now());
        payment.setMethod(method);
        payment.setReferenceCode(referenceCode);
        payment.setStatus("SUCCESS");
        // A unique reference constraint also rejects simultaneous requests on different invoices.
        payments.saveAndFlush(payment);
        invoice.setTotalPaid(updatedTotal);
        invoice.setStatus(updatedTotal.compareTo(invoice.getTotalDue()) == 0 ? "PAID" : "PARTIAL");
        invoices.saveAndFlush(invoice);
        return payment;
    }
}