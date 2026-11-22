package com.yingyou.personalization;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public final class PersonalizationRequests {

    private PersonalizationRequests() {
    }

    public record UpdatePreferencesRequest(@NotNull UUID requestId, long revision,
                                           String examTrack, String difficultyMode,
                                           String manualDifficulty) {
        public String actionDigest() {
            return revision + "|" + (examTrack == null ? "" : examTrack)
                    + "|" + (difficultyMode == null ? "" : difficultyMode)
                    + "|" + (manualDifficulty == null ? "" : manualDifficulty);
        }
    }

    public record CreatePersonalizedSessionRequest(@NotNull UUID requestId, String examTrack) {
    }
}
