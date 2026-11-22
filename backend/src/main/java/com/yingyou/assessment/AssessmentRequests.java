package com.yingyou.assessment;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public final class AssessmentRequests {

    private AssessmentRequests() {
    }

    public record CreateAssessmentRequest(@NotNull UUID requestId) {
    }

    public record SubmitAnswerRequest(@NotNull UUID requestId, long revision,
                                      @NotNull String itemId, String optionId, boolean unknown) {
        public String actionDigest() {
            return revision + "|" + itemId + "|" + (optionId == null ? "" : optionId) + "|" + unknown;
        }
    }
}
