package pl.mkn.tdw.api.aiskills;

import java.util.List;

public final class AiSkillCatalogDtos {

    private AiSkillCatalogDtos() {
    }

    public record AiSkillCatalogResponse(
            String contract,
            int version,
            String mode,
            String source,
            int skillCount,
            int defaultSkillCount,
            int customSkillCount,
            List<AiSkillSummaryResponse> skills
    ) {
    }

    public record AiSkillSummaryResponse(
            String name,
            String description,
            int lineCount,
            String state,
            boolean restoreAvailable
    ) {
    }

    public record AiSkillDetailResponse(
            String contract,
            int version,
            String mode,
            String source,
            String name,
            String description,
            int lineCount,
            String markdown,
            String rawMarkdown,
            String state,
            boolean restoreAvailable
    ) {
    }

    public record AiSkillUpdateRequest(
            String rawMarkdown
    ) {
    }
}
