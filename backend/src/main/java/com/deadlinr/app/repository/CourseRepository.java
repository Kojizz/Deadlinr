package com.deadlinr.app.repository;

import com.deadlinr.app.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Course> findByIdAndUserId(Long id, Long userId);
}
