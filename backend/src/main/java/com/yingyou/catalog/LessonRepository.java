package com.yingyou.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, String> {

    List<Lesson> findByCourseIdOrderBySortOrderAsc(String courseId);

    long countByCourseId(String courseId);
}
