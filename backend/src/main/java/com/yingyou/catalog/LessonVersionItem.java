package com.yingyou.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "lesson_version_item")
public class LessonVersionItem {

    @EmbeddedId
    private LessonVersionItemId id;

    @Column(name = "sentence_version_id", nullable = false)
    private String sentenceVersionId;

    protected LessonVersionItem() {
    }

    public LessonVersionItemId getId() {
        return id;
    }

    public String getSentenceVersionId() {
        return sentenceVersionId;
    }
}
