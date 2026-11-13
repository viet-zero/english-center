package vn.edu.center.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.center.dto.EnrollmentRequest;
import vn.edu.center.service.CurrentUserService;
import vn.edu.center.service.EnrollmentService;
import vn.edu.center.service.PayOsClient;

@RestController
public class EnrollmentController {
    private final EnrollmentService enrollments;
    private final CurrentUserService currentUser;
    private final PayOsClient payOs;

    public EnrollmentController(EnrollmentService enrollments, CurrentUserService currentUser, PayOsClient payOs) {
        this.enrollments = enrollments;
        this.currentUser = currentUser;
        this.payOs = payOs;
    }

    @PostMapping("/api/enrollments/checkout")
    public EnrollmentService.Checkout checkout(Principal principal, @Valid @RequestBody EnrollmentRequest request) {
        String studentId = currentUser.studentId(principal);
        var order = enrollments.openCheckout(studentId, request.classId());
        enrollments.attachLink(order.orderCode(), payOs.create(order.orderCode(), order.amount()));
        return enrollments.checkout(studentId, order.orderCode());
    }

    @GetMapping("/api/enrollments/checkout/{code}")
    public EnrollmentService.Checkout checkout(Principal principal, @PathVariable long code) {
        return enrollments.checkout(currentUser.studentId(principal), code);
    }

    @GetMapping("/api/payments/payos/reviews")
    public List<Map<String, Object>> reviews() {
        return enrollments.paymentReviews();
    }

    @PostMapping("/api/payments/payos/webhook")
    public Map<String, Boolean> webhook(@RequestBody Webhook event) {
        var data = event.data();
        if (!payOs.verify(data, event.signature())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if ("00".equals(data.get("code")) && "VND".equals(data.get("currency"))) {
            long orderCode = positiveInteger(data.get("orderCode"));
            long amount = positiveInteger(data.get("amount"));
            if (!(data.get("paymentLinkId") instanceof String linkId) || linkId.isBlank()
                    || !(data.get("reference") instanceof String reference)
                    || reference.isBlank() || reference.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            enrollments.settle(orderCode, linkId, reference, amount);
        }
        return Map.of("success", true);
    }

    private long positiveInteger(Object value) {
        if (value instanceof Number number && number.toString().matches("[1-9][0-9]{0,17}")) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    public record Webhook(Map<String, Object> data, String signature) {}
}
