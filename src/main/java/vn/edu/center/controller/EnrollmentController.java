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
import vn.edu.center.service.SePayClient;
import vn.edu.center.service.SePayWebhookService;

@RestController
public class EnrollmentController {
    private final EnrollmentService enrollments;
    private final CurrentUserService currentUser;
    private final SePayClient sepay;
    private final SePayWebhookService webhooks;

    public EnrollmentController(EnrollmentService enrollments, CurrentUserService currentUser, SePayClient sepay,
            SePayWebhookService webhooks) {
        this.enrollments = enrollments;
        this.currentUser = currentUser;
        this.sepay = sepay;
        this.webhooks = webhooks;
    }

    @PostMapping("/api/enrollments/checkout")
    public EnrollmentService.Checkout checkout(Principal principal, @Valid @RequestBody EnrollmentRequest request) {
        String studentId = currentUser.studentId(principal);
        var order = enrollments.openCheckout(studentId, request.classId(),request.couponCode());

        return enrollments.checkout(studentId, order.orderCode());
    }

    @GetMapping("/api/enrollments/checkout/{code}")
    public EnrollmentService.Checkout checkout(Principal principal, @PathVariable long code) {
        return enrollments.checkout(currentUser.studentId(principal), code);
    }

    @PostMapping("/api/enrollments/checkout/{code}/confirm-free")
    public EnrollmentService.Checkout confirmFree(Principal principal,@PathVariable long code) {
        return enrollments.confirmFree(currentUser.studentId(principal),code);
    }

    @GetMapping("/api/enrollments/payment-mode")
    public Map<String, Boolean> paymentMode() {
        return Map.of("demo", sepay.isDemo());
    }

    @PostMapping("/api/enrollments/checkout/{code}/demo-payment")
    public EnrollmentService.Checkout demoPayment(Principal principal, @PathVariable long code) {
        if (!sepay.isDemo()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String studentId = currentUser.studentId(principal);
        var order = enrollments.checkout(studentId, code);
        // The stable reference makes retries idempotent.
        if (!"SEPAY".equals(order.provider())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Hãy tạo đơn SePay mới.");
        if (!"PENDING".equals(order.status())) return order;
        if (order.amount()==0) return enrollments.confirmFree(studentId,code);
        webhooks.accept(new SePayWebhookService.Event(code,order.receivingAccount(),
                "in",java.math.BigDecimal.valueOf(order.amount()),order.paymentCode(),order.paymentCode(),"DEMO-"+code));
        return enrollments.checkout(studentId, code);
    }

    @GetMapping({"/api/payments/reviews","/api/payments/payos/reviews"})
    public List<Map<String, Object>> reviews() {
        return enrollments.paymentReviews();
    }

    @GetMapping({"/api/payments/reviews/{reference}/classes","/api/payments/payos/reviews/{reference}/classes"})
    public List<Map<String, Object>> reviewClasses(@PathVariable String reference) {
        return enrollments.reviewClasses(reference);
    }

    @PostMapping({"/api/payments/reviews/{reference}/resolve","/api/payments/payos/reviews/{reference}/resolve"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(Principal principal, @PathVariable String reference, @Valid @RequestBody Resolution request) {
        if ("REFUND_DEMO".equals(request.action()) && !sepay.isDemo()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        enrollments.resolveReview(reference, currentUser.employeeId(principal),
                request.action(), request.targetClassId(), request.note());
    }

    public record Resolution(
            @jakarta.validation.constraints.NotBlank String action,
            String targetClassId,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 255) String note) {}

    @GetMapping("/api/payments/sepay/unmatched")
    public List<Map<String,Object>> unmatched() { return webhooks.unmatched(); }

    @PostMapping("/api/payments/sepay/webhook")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Boolean> webhook(@RequestHeader(value="Authorization",required=false) String authorization,
            @Valid @RequestBody SePayWebhookService.Event event) {
        if (!sepay.authorize(authorization)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        webhooks.accept(event);
        return Map.of("success",true);
    }
}