package com.deadlinr.app.service;

import com.deadlinr.app.dto.CourseRequest;
import com.deadlinr.app.dto.CourseResponse;
import com.deadlinr.app.dto.CourseUpdateRequest;
import com.deadlinr.app.entity.Course;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.exception.ForbiddenException;
import com.deadlinr.app.exception.ResourceNotFoundException;
import com.deadlinr.app.repository.CourseRepository;
import com.deadlinr.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public CourseService(CourseRepository courseRepository, UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> getCourses(User currentUser) {
        return courseRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(CourseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse getCourse(User currentUser, Long id) {
        return CourseResponse.from(findOwnedCourse(currentUser, id));
    }

    @Transactional
    public CourseResponse createCourse(User currentUser, CourseRequest request) {
        Course course = Course.builder()
                .user(userRepository.getReferenceById(currentUser.getId()))
                .name(request.name())
                .code(request.code())
                .instructor(request.instructor())
                .build();

        courseRepository.save(course);
        return CourseResponse.from(course);
    }

    @Transactional
    public CourseResponse updateCourse(User currentUser, Long id, CourseUpdateRequest request) {
        Course course = findOwnedCourse(currentUser, id);
        if (request.name() != null) {
            course.setName(request.name());
        }
        if (request.code() != null) {
            course.setCode(request.code());
        }
        if (request.instructor() != null) {
            course.setInstructor(request.instructor());
        }
        return CourseResponse.from(course);
    }

    @Transactional
    public void deleteCourse(User currentUser, Long id) {
        courseRepository.delete(findOwnedCourse(currentUser, id));
    }

    private Course findOwnedCourse(User currentUser, Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + id));
        if (!course.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("You do not have access to this course");
        }
        return course;
    }
}
