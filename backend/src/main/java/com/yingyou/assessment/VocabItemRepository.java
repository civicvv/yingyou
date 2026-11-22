package com.yingyou.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VocabItemRepository extends JpaRepository<VocabItemEntity, String> {

    @Query(nativeQuery = true, value = """
            select id, band, word,
                   context_en as contextEn,
                   prompt_zh as promptZh,
                   options::text as options
            from vocab_item_version
            where form_version_id = :formId
            order by band, id
            """)
    List<VocabItemRow> findByFormId(@Param("formId") String formId);

    interface VocabItemRow {
        String getId();

        int getBand();

        String getWord();

        String getContextEn();

        String getPromptZh();

        String getOptions();
    }
}
