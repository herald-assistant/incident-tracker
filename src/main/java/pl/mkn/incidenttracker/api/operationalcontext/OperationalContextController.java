package pl.mkn.incidenttracker.api.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OpenQuestionDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextBoundedContextRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextCodeSearchScopeRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextEntityDetailDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextEntityRelationsReadModelDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextGlossaryRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextHandoffRuleRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextIntegrationRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextProcessRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextRepositoryRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSearchResultDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSummaryDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSystemRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.OperationalContextTeamRowDto;
import pl.mkn.incidenttracker.api.operationalcontext.dto.OperationalContextDtos.ValidationFindingDto;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextBlastRadiusReadModel;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextFlowReadModel;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextImplementationReadModel;

import java.util.List;

@RestController
@RequestMapping("/api/operational-context")
@RequiredArgsConstructor
class OperationalContextController {

    private final OperationalContextViewService viewService;

    @GetMapping("/summary")
    OperationalContextSummaryDto summary() {
        return viewService.summary();
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
    List<OperationalContextSearchResultDto> search(@RequestParam(name = "q", required = false) String query) {
        return viewService.search(query);
    }

    @GetMapping("/entities/{type}")
    OperationalContextEntityDetailDto entityByQuery(
            @PathVariable String type,
            @RequestParam String id
    ) {
        return viewService.entity(type, id);
    }

    @GetMapping("/entities/{type}/{id}")
    OperationalContextEntityDetailDto entity(
            @PathVariable String type,
            @PathVariable String id
    ) {
        return viewService.entity(type, id);
    }

    @GetMapping("/read-model/entities/{type}/{id}/relations")
    OperationalContextEntityRelationsReadModelDto entityRelationsReadModel(
            @PathVariable String type,
            @PathVariable String id
    ) {
        return viewService.entityRelationsReadModel(type, id);
    }

    @GetMapping("/read-model/entities/{type}/relations")
    OperationalContextEntityRelationsReadModelDto entityRelationsReadModelByQuery(
            @PathVariable String type,
            @RequestParam String id
    ) {
        return viewService.entityRelationsReadModel(type, id);
    }

    @GetMapping("/read-model/entities/{type}/{id}/code-search")
    OperationalContextCodeSearchReadModel codeSearchReadModel(
            @PathVariable String type,
            @PathVariable String id
    ) {
        return viewService.codeSearchReadModel(type, id);
    }

    @GetMapping("/read-model/entities/{type}/code-search")
    OperationalContextCodeSearchReadModel codeSearchReadModelByQuery(
            @PathVariable String type,
            @RequestParam String id
    ) {
        return viewService.codeSearchReadModel(type, id);
    }

    @GetMapping("/read-model/entities/{type}/{id}/implementations")
    OperationalContextImplementationReadModel implementationReadModel(
            @PathVariable String type,
            @PathVariable String id
    ) {
        return viewService.implementationReadModel(type, id);
    }

    @GetMapping("/read-model/entities/{type}/implementations")
    OperationalContextImplementationReadModel implementationReadModelByQuery(
            @PathVariable String type,
            @RequestParam String id
    ) {
        return viewService.implementationReadModel(type, id);
    }

    @GetMapping("/read-model/entities/{type}/{id}/flow")
    OperationalContextFlowReadModel flowReadModel(
            @PathVariable String type,
            @PathVariable String id
    ) {
        return viewService.flowReadModel(type, id);
    }

    @GetMapping("/read-model/entities/{type}/flow")
    OperationalContextFlowReadModel flowReadModelByQuery(
            @PathVariable String type,
            @RequestParam String id
    ) {
        return viewService.flowReadModel(type, id);
    }

    @GetMapping("/read-model/entities/{type}/{id}/blast-radius")
    OperationalContextBlastRadiusReadModel blastRadiusReadModel(
            @PathVariable String type,
            @PathVariable String id
    ) {
        return viewService.blastRadiusReadModel(type, id);
    }

    @GetMapping("/read-model/entities/{type}/blast-radius")
    OperationalContextBlastRadiusReadModel blastRadiusReadModelByQuery(
            @PathVariable String type,
            @RequestParam String id
    ) {
        return viewService.blastRadiusReadModel(type, id);
    }

    @GetMapping("/read-model/blast-radius")
    OperationalContextBlastRadiusReadModel blastRadiusReadModelBySignal(
            @RequestParam String type,
            @RequestParam String id
    ) {
        return viewService.blastRadiusReadModel(type, id);
    }
}
