package pl.mkn.tdw.api.aiskills;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillCatalogResponse;
import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillDetailResponse;

@RestController
@RequestMapping("/api/ai/skills")
@RequiredArgsConstructor
public class AiSkillCatalogController {

    private final AiSkillCatalogService skillCatalogService;

    @GetMapping
    public AiSkillCatalogResponse catalog() {
        return skillCatalogService.catalog();
    }

    @GetMapping("/{skillName}")
    public AiSkillDetailResponse detail(@PathVariable String skillName) {
        return skillCatalogService.detail(skillName);
    }
}
