package vn.edu.center.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.center.entity.Invoice;
import vn.edu.center.entity.Payment;
import vn.edu.center.repository.InvoiceRepository;
import vn.edu.center.repository.PaymentRepository;

class PaymentServiceTest {
    private InvoiceRepository invoices;
    private PaymentRepository payments;
    private PaymentService service;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        invoices = mock(InvoiceRepository.class, withSettings().mockMaker("mock-maker-subclass"));
        payments = mock(PaymentRepository.class, withSettings().mockMaker("mock-maker-subclass"));
        service = new PaymentService(invoices, payments, mock(NotificationService.class, withSettings().mockMaker("mock-maker-subclass")));
        invoice = new Invoice();
        invoice.setId("invoice");
        invoice.setTotalDue(new BigDecimal("100.00"));
        invoice.setTotalPaid(BigDecimal.ZERO);
        when(invoices.findLockedById("invoice")).thenReturn(Optional.of(invoice));
        when(payments.countAccountantRole("staff")).thenReturn(1L);
        when(payments.sumSuccessfulPayments("invoice")).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void retryReturnsExistingPaymentWithoutChargingAgain() {
        Payment previous = new Payment();
        previous.setInvoiceId("invoice");
        previous.setCollectedBy("staff");
        previous.setAmount(new BigDecimal("50.00"));
        previous.setMethod("CASH");
        previous.setStatus("SUCCESS");
        when(payments.findByReferenceCode("ref")).thenReturn(Optional.of(previous));
        assertSame(previous, service.recordPayment("invoice", "staff",
                new BigDecimal("50"), "CASH", "ref"));
        verify(payments, never()).saveAndFlush(any(Payment.class));
        verify(invoices, never()).saveAndFlush(any(Invoice.class));
    }

    @Test
    void rejectsOverpaymentBeforeWriting() {
        when(payments.sumSuccessfulPayments("invoice")).thenReturn(new BigDecimal("80"));
        assertThrows(IllegalStateException.class, () -> service.recordPayment(
                "invoice", "staff", new BigDecimal("30"), "CASH", "ref"));
        verify(payments, never()).saveAndFlush(any(Payment.class));
    }

    @Test
    void usesSuccessfulReceiptsInsteadOfStaleInvoiceTotal() {
        when(payments.sumSuccessfulPayments("invoice")).thenReturn(new BigDecimal("80"));
        service.recordPayment("invoice", "staff", new BigDecimal("20"), "CASH", "ref");
        assertEquals(new BigDecimal("100.00"), invoice.getTotalPaid());
        assertEquals("PAID", invoice.getStatus());
        var order = inOrder(invoices, payments);
        order.verify(invoices).findLockedById("invoice");
        order.verify(payments).countAccountantRole("staff");
        order.verify(payments).findByReferenceCode("ref");
        order.verify(payments).sumSuccessfulPayments("invoice");
        order.verify(payments).saveAndFlush(any(Payment.class));
        order.verify(invoices).saveAndFlush(invoice);
    }
}
