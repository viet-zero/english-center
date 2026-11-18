package vn.edu.center.controller;

import java.time.LocalDateTime;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.center.service.CouponService;

@RestController
@RequestMapping("/api/admin/discounts")
public class CouponController {
    private final CouponService coupons;
    public CouponController(CouponService coupons) { this.coupons=coupons; }
    @GetMapping public List<Map<String,Object>> list() { return coupons.list(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Map<String,String> create(@Valid @RequestBody Create body) {
        return Map.of("id",coupons.create(body.code(),body.courseId(),body.amount(),body.startsAt(),body.expiresAt(),body.maxUses(),body.status()));
    }
    @PutMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable String id,@Valid @RequestBody Update body) {
        coupons.update(id,body.startsAt(),body.expiresAt(),body.maxUses(),body.status());
    }
    public record Create(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{3,30}") String code,
            @NotBlank @Size(max=36) String courseId,@NotNull @Positive Integer amount,
            @NotNull LocalDateTime startsAt,@NotNull LocalDateTime expiresAt,@NotNull @Positive Integer maxUses,
            @NotBlank @Pattern(regexp="ACTIVE|INACTIVE") String status) {}
    public record Update(@NotNull LocalDateTime startsAt,@NotNull LocalDateTime expiresAt,
            @NotNull @Positive Integer maxUses,@NotBlank @Pattern(regexp="ACTIVE|INACTIVE") String status) {}
}
