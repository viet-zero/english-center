package vn.edu.center.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.center.entity.Course;
import vn.edu.center.entity.Module;
import vn.edu.center.service.CourseService;
import vn.edu.center.service.CourseService.CourseDetails;
import vn.edu.center.service.CourseService.ModuleDetails;
import vn.edu.center.service.CourseService.ModuleRequest;

@RestController
@RequestMapping("/api")
public class CourseController {
    private final CourseService catalog;

    public CourseController(CourseService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/catalog/courses")
    public List<Course> activeCourses() {
        return catalog.listCourses(false);
    }

    @GetMapping("/catalog/courses/{courseId}/modules")
    public List<Module> activeModules(@PathVariable String courseId) {
        return catalog.listModules(courseId, false);
    }

    @GetMapping("/training/courses")
    public List<Course> allCourses() {
        return catalog.listCourses(true);
    }

    @GetMapping("/training/courses/{courseId}/modules")
    public List<Module> allModules(@PathVariable String courseId) {
        return catalog.listModules(courseId, true);
    }

    @PostMapping("/training/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public Course createCourse(@Valid @RequestBody CourseDetails request) {
        return catalog.createCourse(request);
    }

    @PutMapping("/training/courses/{courseId}")
    public Course updateCourse(@PathVariable String courseId, @Valid @RequestBody CourseDetails request) {
        return catalog.updateCourse(courseId, request);
    }

    @PostMapping("/training/courses/{courseId}/modules")
    @ResponseStatus(HttpStatus.CREATED)
    public Module createModule(@PathVariable String courseId, @Valid @RequestBody ModuleRequest request) {
        return catalog.createModule(courseId, request);
    }

    @PutMapping("/training/modules/{moduleId}")
    public Module updateModule(@PathVariable String moduleId, @Valid @RequestBody ModuleDetails request) {
        return catalog.updateModule(moduleId, request);
    }
}
