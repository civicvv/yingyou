package com.yingyou.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "course")
public class Course {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_track", nullable = false, length = 20)
    private ExamTrack examTrack;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CourseStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Course() {
    }

    public String getId() {
        return id;
    }

    public ExamTrack getExamTrack() {
        return examTrack;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
