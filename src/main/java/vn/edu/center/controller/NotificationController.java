package vn.edu.center.controller;

import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.center.service.CurrentUserService;
import vn.edu.center.service.NotificationService;

@RestController
@RequestMapping("/api/academics/student/notifications")
public class NotificationController {
    private final CurrentUserService currentUser;
    private final NotificationService notifications;

    public NotificationController(CurrentUserService currentUser, NotificationService notifications) {
        this.currentUser = currentUser;
        this.notifications = notifications;
    }

    @GetMapping
    public NotificationService.Inbox inbox(Principal principal) {
        return notifications.inbox(currentUser.studentId(principal));
    }

    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(Principal principal, @PathVariable long id) {
        notifications.markRead(currentUser.studentId(principal), id);
    }
}
