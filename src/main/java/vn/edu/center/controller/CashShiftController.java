package vn.edu.center.controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import vn.edu.center.service.*;

@RestController
@RequestMapping("/api/accounting")
public class CashShiftController {
    private final CashShiftService shifts;
    private final CurrentUserService users;
    public CashShiftController(CashShiftService shifts,CurrentUserService users) {
        this.shifts=shifts;
        this.users=users;
    }
    @GetMapping("/shifts")
    public Map<String,Object> list(Principal principal) { return shifts.shifts(users.employeeId(principal)); }
    @PostMapping("/shifts")
    public Map<String,Object> open(Principal principal,@Valid @RequestBody Open body) {
        return shifts.open(users.employeeId(principal),body.id(),body.openingCash());
    }
    @PostMapping("/shifts/{id}/close")
    public Map<String,Object> close(Principal principal,@PathVariable String id,@Valid @RequestBody Close body) {
        return shifts.close(users.employeeId(principal),id,body.actualCash(),body.note());
    }
    @GetMapping("/revenue")
    public Map<String,Object> report(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        return shifts.revenue(from,to);
    }
    public record Open(@NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String id,
            @NotNull @DecimalMin("0") @Digits(integer=13,fraction=2) BigDecimal openingCash) {}
    public record Close(@NotNull @DecimalMin("0") @Digits(integer=13,fraction=2) BigDecimal actualCash,
            @Size(max=1000) String note) {}
}
