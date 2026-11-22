package com.yingyou.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class LessonVersionItemId implements Serializable {

    @Column(name = "lesson_version_id")
    private String lessonVersionId;

    @Column(name = "position")
    private int position;

    protected LessonVersionItemId() {
    }

    public LessonVersionItemId(String lessonVersionId, int position) {
        this.lessonVersionId = lessonVersionId;
        this.position = position;
    }

    public String getLessonVersionId() {
        return lessonVersionId;
    }

    public int getPosition() {
        return position;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LessonVersionItemId that)) {
            return false;
        }
        return position == that.position && Objects.equals(lessonVersionId, that.lessonVersionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lessonVersionId, position);
    }
}
