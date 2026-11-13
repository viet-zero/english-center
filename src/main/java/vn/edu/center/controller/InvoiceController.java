package vn.edu.center.controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import vn.edu.center.dto.PaymentRequest;
import vn.edu.center.service.*;

@RestController
@Validated
@RequestMapping("/api/invoices")
public class InvoiceController {
    private final InvoiceService invoices;
    private final PaymentService payments;
    private final CurrentUserService currentUser;

    public InvoiceController(InvoiceService invoices, PaymentService payments, CurrentUserService currentUser) {
        this.invoices = invoices;
        this.payments = payments;
        this.currentUser = currentUser;
    }

    @GetMapping("/{invoiceId}")
    public InvoiceService.InvoiceResponse getInvoice(
            Principal principal, @PathVariable @Size(max = 36) String invoiceId) {
        return invoices.findForStudent(invoiceId, currentUser.studentId(principal));
    }

    @PostMapping("/{invoiceId}/payments")
    public PaymentResponse recordPayment(Principal principal,
            @PathVariable @Size(max = 36) String invoiceId,
            @Valid @RequestBody PaymentRequest request) {
        var payment = payments.recordPayment(invoiceId, currentUser.employeeId(principal),
                request.amount(), request.method(), request.referenceCode());
        return new PaymentResponse(payment.getId(), payment.getInvoiceId(), payment.getAmount(),
                payment.getPaidAt(), payment.getStatus(), payment.getReferenceCode());
    }

    public record PaymentResponse(String id, String invoiceId, BigDecimal amount,
            LocalDateTime paidAt, String status, String referenceCode) {}
}