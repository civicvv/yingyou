package com.yingyou.practice;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * 读取不可变题目版本内容；答案变体与讲解段以 JSON 保存。
 */
@Repository
public class SentenceContentRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public SentenceContentRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public Optional<SentenceContent> findById(String sentenceVersionId) {
        return jdbcClient.sql("""
                        select id, prompt_zh, context_zh, canonical_en,
                               answer_variants::text as answer_variants,
                               explanation_segments::text as explanation_segments,
                               explanation_zh, grammar_tags::text as grammar_tags
                        from sentence_version
                        where id = :id
                        """)
                .param("id", sentenceVersionId)
                .query((rs, rowNum) -> map(rs))
                .list()
                .stream()
                .findFirst();
    }

    private SentenceContent map(ResultSet rs) throws SQLException {
        String[] variants = objectMapper.readValue(rs.getString("answer_variants"), String[].class);
        ExplanationSegmentView[] segments = objectMapper.readValue(
                rs.getString("explanation_segments"), ExplanationSegmentView[].class);
        String[] grammarTags = objectMapper.readValue(rs.getString("grammar_tags"), String[].class);
        return new SentenceContent(
                rs.getString("id"),
                rs.getString("prompt_zh"),
                rs.getString("context_zh"),
                rs.getString("canonical_en"),
                List.of(variants),
                List.of(segments),
                rs.getString("explanation_zh"),
                List.of(grammarTags));
    }

    public record SentenceContent(
            String id,
            String promptZh,
            String contextZh,
            String canonicalEn,
            List<String> answerVariants,
            List<ExplanationSegmentView> explanationSegments,
            String explanationZh,
            List<String> grammarTags
    ) {
    }
}
