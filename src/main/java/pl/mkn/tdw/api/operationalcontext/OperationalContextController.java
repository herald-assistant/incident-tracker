package pl.mkn.tdw.api.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OpenQuestionDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextBoundedContextRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextCodeSearchScopeRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextGlossaryRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextHandoffRuleRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextIntegrationRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextProcessRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextRepositoryRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSystemRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextTeamRowDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ValidationFindingDto;

import java.util.List;

@RestController
@RequestMapping("/api/operational-context")
@RequiredArgsConstructor
class OperationalContextController {

    private final OperationalContextViewService viewService;

    @GetMapping("/summary")
    Object summary(@RequestParam(name = "profile", required = false) String profile) {
        return StringUtils.hasText(profile) ? viewService.summary(profile) : viewService.summary();
    }

    @GetMapping("/systems")
    List<OperationalContextSystemRowDto> systems() {
        return viewService.systems();
    }

    @GetMapping("/repositories")
    List<OperationalContextRepositoryRowDto> repositories() {
        return viewService.repositories();
    }

    @GetMapping("/code-search-scopes")
    List<OperationalContextCodeSearchScopeRowDto> codeSearchScopes() {
        return viewService.codeSearchScopes();
    }

    @GetMapping("/processes")
    List<OperationalContextProcessRowDto> processes() {
        return viewService.processes();
    }

    @GetMapping("/integrations")
    List<OperationalContextIntegrationRowDto> integrations() {
        return viewService.integrations();
    }

    @GetMapping("/bounded-contexts")
    List<OperationalContextBoundedContextRowDto> boundedContexts() {
        return viewService.boundedContexts();
    }

    @GetMapping("/teams")
    List<OperationalContextTeamRowDto> teams() {
        return viewService.teams();
    }

    @GetMapping("/glossary")
    List<OperationalContextGlossaryRowDto> glossary() {
        return viewService.glossary();
    }

    @GetMapping("/handoff-rules")
    List<OperationalContextHandoffRuleRowDto> handoffRules() {
        return viewService.handoffRules();
    }

    @GetMapping("/open-questions")
    List<OpenQuestionDto> openQuestions() {
        return viewService.openQuestions();
    }

    @GetMapping("/validation")
    List<ValidationFindingDto> validation() {
        return viewService.validation();
    }

    @GetMapping("/search")
    Object search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "profile", required = false) String profile
    ) {
        return StringUtils.hasText(profile) ? viewService.search(query, profile) : viewService.search(query);
    }

    @GetMapping("/entities/{type}")
    Object entityByQuery(
            @PathVariable String type,
            @RequestParam String id,
            @RequestParam(name = "profile", required = false) String profile
    ) {
        return StringUtils.hasText(profile)
                ? viewService.entity(type, id, profile)
                : viewService.entity(type, id);
    }

    @GetMapping("/entities/{type}/{id}")
    Object entity(
            @PathVariable String type,
            @PathVariable String id,
            @RequestParam(name = "profile", required = false) String profile
    ) {
        return StringUtils.hasText(profile) ? viewService.entity(type, id, profile) : viewService.entity(type, id);
    }

    @GetMapping("/read-model/entities/{type}/{id}/relations")
    Object entityRelationsReadModel(
            @PathVariable String type,
            @PathVariable String id,
            @RequestParam(name = "profile", required = false) String profile
    ) {
        return StringUtils.hasText(profile)
                ? viewService.entityRelationsReadModel(type, id, profile)
                : viewService.entityRelationsReadModel(type, id);
    }

    @GetMapping("/read-model/entities/{type}/relations")
    Object entityRelationsReadModelByQuery(
            @PathVariable String type,
            @RequestParam String id,
            @RequestParam(name = "profile", required = false) String profile
    ) {
        return StringUtils.hasText(profile)
                ? viewService.entityRelationsReadModel(type, id, profile)
                : viewService.entityRelationsReadModel(type, id);
    }

    @GetMapping("/read-model/entities/{type}/{id}/code-search")
    Object codeSearchReadModel(
            @PathVariable String type,
            @PathVariable String id,
            @RequestParam(name = "profile", required = false) String profile
    ) {
        return StringUtils.hasText(profile)
                ? viewService.codeSearchReadModel(type, id, profile)
                : viewService.codeSearchReadModel(type, id);
    }

    @GetMapping("/read-model/entities/{type}/code-search")
    Object codeSearchReadModelByQuery(
            @PathVariable String type,
            @RequestParam String id,
            @RequestParam(name = "profile", required = false) String profile
    ) {
        return StringUtils.hasText(profile)
                ? viewService.codeSearchReadModel(type, id, profile)
                : viewService.codeSearchReadModel(type, id);
    }

}
