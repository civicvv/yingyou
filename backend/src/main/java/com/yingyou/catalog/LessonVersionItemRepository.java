package com.yingyou.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LessonVersionItemRepository extends JpaRepository<LessonVersionItem, LessonVersionItemId> {

    @Query("select count(item) from LessonVersionItem item where item.id.lessonVersionId = :lessonVersionId")
    long countByLessonVersionId(@Param("lessonVersionId") String lessonVersionId);

    @Query("""
            select item from LessonVersionItem item
            where item.id.lessonVersionId = :lessonVersionId
            order by item.id.position
            """)
    List<LessonVersionItem> findByLessonVersionIdOrderByPosition(@Param("lessonVersionId") String lessonVersionId);
}
