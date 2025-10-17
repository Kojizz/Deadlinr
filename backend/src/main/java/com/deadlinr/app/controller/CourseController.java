package com.deadlinr.app.controller;

import com.deadlinr.app.dto.CourseRequest;
import com.deadlinr.app.dto.CourseResponse;
import com.deadlinr.app.dto.CourseUpdateRequest;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.service.CourseService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@SecurityRequirement(name = "bearerAuth")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ResponseEntity<List<CourseResponse>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(courseService.getCourses(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseResponse> get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourse(user, id));
    }

    @PostMapping
    public ResponseEntity<CourseResponse> create(@AuthenticationPrincipal User user,
                                                 @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.createCourse(user, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CourseResponse> update(@AuthenticationPrincipal User user,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody CourseUpdateRequest request) {
        return ResponseEntity.ok(courseService.updateCourse(user, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        courseService.deleteCourse(user, id);
        return ResponseEntity.noContent().build();
    }
}
