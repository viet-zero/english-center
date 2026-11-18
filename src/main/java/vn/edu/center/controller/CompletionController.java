package vn.edu.center.controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import vn.edu.center.service.*;

@RestController
public class CompletionController {
    private final CompletionService completion;
    private final CurrentUserService users;

    public CompletionController(CompletionService completion, CurrentUserService users) {
        this.completion=completion;
        this.users=users;
    }

    @GetMapping("/api/training/completion/classes")
    public List<Map<String,Object>> classes() { return completion.classes(null); }

    @GetMapping("/api/training/completion/classes/{id}")
    public Map<String,Object> preview(@PathVariable String id) { return completion.preview(id,null); }

    @GetMapping("/api/academics/teacher/completion/classes")
    public List<Map<String,Object>> teacherClasses(Principal principal) {
        return completion.classes(users.employeeId(principal));
    }

    @GetMapping("/api/academics/teacher/completion/classes/{id}")
    public Map<String,Object> teacherPreview(Principal principal,@PathVariable String id) {
        return completion.preview(id,users.employeeId(principal));
    }

    @PutMapping("/api/training/completion/classes/{id}/policy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void policy(@PathVariable String id,@Valid @RequestBody Policy body) {
        completion.policy(id,body.passScore(),body.minimumAttendance(),body.weights());
    }

    @PostMapping("/api/training/completion/classes/{id}/finalize")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finalizeClass(Principal principal,@PathVariable String id) {
        completion.finalizeClass(id,users.employeeId(principal));
    }

    @PostMapping("/api/training/completion/classes/{id}/certificates/{enrollment}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void issue(Principal principal,@PathVariable String id,@PathVariable String enrollment) {
        completion.issue(id,enrollment,users.employeeId(principal));
    }

    @GetMapping("/api/academics/student/completion")
    public List<Map<String,Object>> results(Principal principal) { return completion.results(users.studentId(principal)); }

    @GetMapping("/api/academics/student/certificates")
    public List<Map<String,Object>> certificates(Principal principal) { return completion.certificates(users.studentId(principal)); }

    @GetMapping(value="/api/academics/student/certificates/{id}/pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(Principal principal,@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=certificate.pdf")
                .body(completion.download(users.studentId(principal),id));
    }

    @GetMapping(value="/api/certificates/verify/{token}",produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verify(@PathVariable String token) {
        var certificate=completion.verify(token);
        String html="""
                <!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
                <title>Tra cứu chứng chỉ English Center</title>
                <body style="font-family:Arial,sans-serif;background:#eaeaea;color:#333;padding:32px">
                <main style="max-width:640px;margin:auto;background:white;padding:32px;border-radius:12px">
                <h1 style="color:#2e3192">Chứng chỉ hợp lệ</h1>
                <p>English Center</p><p>Mã chứng chỉ <strong>%s</strong></p>
                <p>Học viên %s</p><p>Khóa học %s</p><p>Ngày cấp %s</p></main></body></html>
                """.formatted(escape(certificate.get("code")),escape(certificate.get("studentName")),
                        escape(certificate.get("courseName")),escape(certificate.get("issuedAt")));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'")
                .body(html);
    }

    private String escape(Object text) { return HtmlUtils.htmlEscape(String.valueOf(text)); }

    public record Policy(@NotNull @DecimalMin("0") @DecimalMax("10") @Digits(integer=2,fraction=2) BigDecimal passScore,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal minimumAttendance,
            @NotNull @Size(max=200) Map<@NotBlank String,@NotNull @DecimalMin("0.01") @DecimalMax("9999.99") @Digits(integer=4,fraction=2) BigDecimal> weights) {}
}
