package vn.edu.center.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.center.entity.Classroom;
import vn.edu.center.service.CourseService;
import vn.edu.center.service.CourseService.ClassDetails;
import vn.edu.center.service.CourseService.ClassRequest;
import vn.edu.center.service.CourseService.OpenClass;

@RestController
@RequestMapping("/api")
public class ClassController {
    private final CourseService catalog;

    public ClassController(CourseService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/catalog/modules/{moduleId}/classes")
    public List<OpenClass> openClasses(@PathVariable String moduleId) {
        return catalog.listOpenClasses(moduleId);
    }

    @GetMapping("/training/modules/{moduleId}/classes")
    public List<Classroom> allClasses(@PathVariable String moduleId) {
        return catalog.listManagedClasses(moduleId);
    }

    @PostMapping("/training/modules/{moduleId}/classes")
    @ResponseStatus(HttpStatus.CREATED)
    public Classroom createClass(@PathVariable String moduleId, @Valid @RequestBody ClassRequest request) {
        return catalog.createClass(moduleId, request);
    }

    @PutMapping("/training/classes/{classId}")
    public Classroom updateClass(@PathVariable String classId, @Valid @RequestBody ClassDetails request) {
        return catalog.updateClass(classId, request);
    }
}
