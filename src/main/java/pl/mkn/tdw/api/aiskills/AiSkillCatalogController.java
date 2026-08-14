package pl.mkn.tdw.api.aiskills;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillCatalogResponse;
import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillDetailResponse;
import static pl.mkn.tdw.api.aiskills.AiSkillCatalogDtos.AiSkillUpdateRequest;

@RestController
@RequestMapping("/api/ai/skills")
@RequiredArgsConstructor
public class AiSkillCatalogController {

    private final AiSkillCatalogService skillCatalogService;

    @GetMapping
    public ResponseEntity<AiSkillCatalogResponse> catalog() {
        return noStore(skillCatalogService.catalog());
    }

    @GetMapping("/{skillName}")
    public ResponseEntity<AiSkillDetailResponse> detail(@PathVariable String skillName) {
        return noStore(skillCatalogService.detail(skillName));
    }

    @PutMapping("/{skillName}")
    public ResponseEntity<AiSkillDetailResponse> update(
            @PathVariable String skillName,
            @RequestBody AiSkillUpdateRequest request
    ) {
        return noStore(skillCatalogService.update(skillName, request));
    }

    @PostMapping("/{skillName}/restore-default")
    public ResponseEntity<AiSkillDetailResponse> restoreDefault(@PathVariable String skillName) {
        return noStore(skillCatalogService.restoreDefault(skillName));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
