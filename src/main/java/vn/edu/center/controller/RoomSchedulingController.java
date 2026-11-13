package vn.edu.center.controller;

import java.time.LocalDate;
import java.security.Principal;
import vn.edu.center.service.CurrentUserService;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.center.service.RoomSchedulingService;
import vn.edu.center.service.RoomSchedulingService.Shift;

@RestController
@RequestMapping("/api/training/room-scheduling")
public class RoomSchedulingController {
    private final RoomSchedulingService scheduling;

    private final CurrentUserService currentUser;

    public RoomSchedulingController(RoomSchedulingService scheduling, CurrentUserService currentUser) {
        this.currentUser = currentUser;
        this.scheduling = scheduling;
    }

    @GetMapping("/classes")
    public List<Map<String, Object>> classes() {
        return scheduling.classes();
    }

    @GetMapping("/rooms")
    public List<Map<String, Object>> rooms(@RequestParam LocalDate day, @RequestParam Shift shift) {
        return scheduling.rooms(day, shift);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> schedule(Principal principal, @Valid @RequestBody ScheduleRequest request) {
        return Map.of("id", scheduling.schedule(request.classId(), request.roomId(), request.day(), request.shift(), currentUser.employeeId(principal)));
    }

    public record ScheduleRequest(@NotBlank @Size(max = 36) String classId,
            @NotBlank @Size(max = 36) String roomId, @NotNull LocalDate day, @NotNull Shift shift) {}
}
