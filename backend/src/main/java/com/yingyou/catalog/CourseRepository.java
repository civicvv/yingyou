package com.yingyou.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, String> {

    List<Course> findByStatusOrderBySortOrderAsc(CourseStatus status);

    List<Course> findByStatusAndExamTrackOrderBySortOrderAsc(CourseStatus status, ExamTrack examTrack);
}
