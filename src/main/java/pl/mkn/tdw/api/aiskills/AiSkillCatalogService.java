package pl.mkn.tdw.api.aiskills;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkill;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;

import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillCatalogResponse;
import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillDetailResponse;
import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillSummaryResponse;

@Service
@RequiredArgsConstructor
public class AiSkillCatalogService {

    private static final String CATALOG_CONTRACT = "ai-skills.catalog";
    private static final String DETAIL_CONTRACT = "ai-skills.detail";
    private static final int CONTRACT_VERSION = 1;
    private static final String MODE = "READ_ONLY";
    private static final String SOURCE = "COPILOT_RUNTIME";

    private final CopilotSkillRuntimeLoader skillRuntimeLoader;

    public AiSkillCatalogResponse catalog() {
        var skills = skillRuntimeLoader.availableSkills();
        var summaries = skills.stream()
                .map(this::toSummary)
                .toList();
        return new AiSkillCatalogResponse(
                CATALOG_CONTRACT,
                CONTRACT_VERSION,
                MODE,
                SOURCE,
                summaries.size(),
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

    private AiSkillSummaryResponse toSummary(CopilotRuntimeSkill skill) {
        return new AiSkillSummaryResponse(skill.name(), skill.description(), skill.lineCount());
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
                skill.rawMarkdown()
        );
    }
}
