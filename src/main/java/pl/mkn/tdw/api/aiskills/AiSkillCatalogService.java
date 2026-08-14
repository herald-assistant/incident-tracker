package pl.mkn.tdw.api.aiskills;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkill;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillCatalogException;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillCatalogResponse;
import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillDetailResponse;
import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillSummaryResponse;
import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillUpdateRequest;
import static pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkillState.DEFAULT;

@Service
@RequiredArgsConstructor
public class AiSkillCatalogService {

    private static final String CATALOG_CONTRACT = "ai-skills.catalog";
    private static final String DETAIL_CONTRACT = "ai-skills.detail";
    private static final int CONTRACT_VERSION = 2;
    private static final String MODE = "EDITABLE";
    private static final String SOURCE = "COPILOT_RUNTIME";

    private final CopilotSkillRuntimeLoader skillRuntimeLoader;

    public AiSkillCatalogResponse catalog() {
        var skills = skillRuntimeLoader.availableSkills();
        var summaries = skills.stream()
                .map(this::toSummary)
                .toList();
        var defaultSkillCount = skills.stream().filter(skill -> skill.state() == DEFAULT).count();
        return new AiSkillCatalogResponse(
                CATALOG_CONTRACT,
                CONTRACT_VERSION,
                MODE,
                SOURCE,
                summaries.size(),
                Math.toIntExact(defaultSkillCount),
                summaries.size() - Math.toIntExact(defaultSkillCount),
                summaries
        );
    }

    public AiSkillDetailResponse detail(String skillName) {
        return skillRuntimeLoader.availableSkills().stream()
                .filter(skill -> skill.name().equals(skillName))
                .findFirst()
                .map(this::toDetail)
                .orElseThrow(() -> new AiSkillNotFoundException(skillName));
    }

    public AiSkillDetailResponse update(String skillName, AiSkillUpdateRequest request) {
        if (request == null) {
            throw new AiSkillCatalogMutationException(
                    "AI_SKILL_VALIDATION_FAILED",
                    UserFacingErrorType.UNPROCESSABLE_ENTITY,
                    "AI skill update request is required."
            );
        }
        try {
            return toDetail(skillRuntimeLoader.updateSkill(skillName, request.rawMarkdown()));
        } catch (CopilotSkillCatalogException exception) {
            throw toApiException(skillName, exception);
        }
    }

    public AiSkillDetailResponse restoreDefault(String skillName) {
        try {
            return toDetail(skillRuntimeLoader.restoreDefault(skillName));
        } catch (CopilotSkillCatalogException exception) {
            throw toApiException(skillName, exception);
        }
    }

    private AiSkillSummaryResponse toSummary(CopilotRuntimeSkill skill) {
        return new AiSkillSummaryResponse(
                skill.name(),
                skill.description(),
                skill.lineCount(),
                skill.state().name(),
                skill.restoreAvailable()
        );
    }

    private AiSkillDetailResponse toDetail(CopilotRuntimeSkill skill) {
        return new AiSkillDetailResponse(
                DETAIL_CONTRACT,
                CONTRACT_VERSION,
                MODE,
                SOURCE,
                skill.name(),
                skill.description(),
                skill.lineCount(),
                skill.markdown(),
                skill.rawMarkdown(),
                skill.state().name(),
                skill.restoreAvailable()
        );
    }

    private RuntimeException toApiException(String skillName, CopilotSkillCatalogException exception) {
        return switch (exception.code()) {
            case SKILL_NOT_FOUND -> new AiSkillNotFoundException(skillName);
            case INVALID_CONTENT -> new AiSkillCatalogMutationException(
                    "AI_SKILL_VALIDATION_FAILED",
                    UserFacingErrorType.UNPROCESSABLE_ENTITY,
                    exception.getMessage()
            );
            case NAME_MISMATCH -> new AiSkillCatalogMutationException(
                    "AI_SKILL_NAME_MISMATCH",
                    UserFacingErrorType.CONFLICT,
                    exception.getMessage()
            );
            case DEFAULT_UNAVAILABLE -> new AiSkillCatalogMutationException(
                    "AI_SKILL_DEFAULT_UNAVAILABLE",
                    UserFacingErrorType.CONFLICT,
                    exception.getMessage()
            );
            case STORAGE_UNAVAILABLE -> new AiSkillCatalogMutationException(
                    "AI_SKILL_CATALOG_UNAVAILABLE",
                    UserFacingErrorType.SERVICE_UNAVAILABLE,
                    exception.getMessage()
            );
        };
    }
}
