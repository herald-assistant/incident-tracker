package pl.mkn.tdw.api.gitlab;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryEndpointListRequest;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryEndpointListResult;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointPort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabMergeRequestSearchResult;
import pl.mkn.tdw.integrations.gitlab.GitLabSettingsPort;
import pl.mkn.tdw.integrations.gitlab.GitLabMergeRequestPort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositorySearchRequest;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositorySearchResponse;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchPort;
import pl.mkn.tdw.integrations.gitlab.GitLabInstructionContextPort;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionContextResult;
import pl.mkn.tdw.integrations.gitlab.contract.openapi.GitLabOpenApiEndpointSliceRequest;
import pl.mkn.tdw.integrations.gitlab.contract.openapi.GitLabOpenApiEndpointSliceResponse;
import pl.mkn.tdw.integrations.gitlab.GitLabOpenApiEndpointSlicePort;
import pl.mkn.tdw.integrations.gitlab.contract.source.GitLabJavaMethodSliceRequest;
import pl.mkn.tdw.integrations.gitlab.contract.source.GitLabJavaMethodSliceResponse;
import pl.mkn.tdw.integrations.gitlab.GitLabJavaMethodSlicePort;
import pl.mkn.tdw.integrations.gitlab.contract.usecase.GitLabEndpointUseCaseContextResult;
import pl.mkn.tdw.integrations.gitlab.GitLabEndpointUseCaseContextPort;
import pl.mkn.tdw.integrations.gitlab.contract.usecase.GitLabJavaMethodUseCaseContextResult;
import pl.mkn.tdw.integrations.gitlab.GitLabJavaMethodUseCaseContextPort;

@RestController
@RequestMapping("/api/gitlab/repository")
@RequiredArgsConstructor
public class GitLabRepositorySearchController {

    private final GitLabRepositorySearchPort gitLabRepositorySearchService;
    private final GitLabRepositoryEndpointPort gitLabRepositoryEndpointService;
    private final GitLabEndpointUseCaseContextPort gitLabEndpointUseCaseContextService;
    private final GitLabJavaMethodUseCaseContextPort gitLabJavaMethodUseCaseContextService;
    private final GitLabRepositoryFilesByPathApiService gitLabRepositoryFilesByPathApiService;
    private final GitLabJavaMethodSlicePort gitLabJavaMethodSliceService;
    private final GitLabOpenApiEndpointSlicePort gitLabOpenApiEndpointSliceService;
    private final GitLabMergeRequestPort gitLabRepositoryPort;
    private final GitLabSettingsPort gitLabProperties;
    private final GitLabInstructionContextPort instructionContextDiscoveryService;

    @PostMapping("/search")
    public GitLabRepositorySearchResponse search(@Valid @RequestBody GitLabRepositorySearchRequest request) {
        return gitLabRepositorySearchService.search(request);
    }

    @PostMapping("/merge-requests/by-issue")
    public GitLabMergeRequestSearchResult findMergeRequestsByIssueKey(
            @Valid @RequestBody GitLabMergeRequestSearchApiRequest request
    ) {
        return gitLabRepositoryPort.findMergeRequestsByIssueKey(
                request.group(),
                request.issueKey(),
                request.maxResults() != null ? request.maxResults() : gitLabProperties.getMaxMergeRequests()
        );
    }

    @PostMapping("/instructions/context")
    public InstructionContextResult discoverInstructionContext(
            @Valid @RequestBody GitLabInstructionContextApiRequest request
    ) {
        return instructionContextDiscoveryService.discover(request.toInstructionContextRequest());
    }

    @PostMapping("/endpoints")
    public GitLabRepositoryEndpointListResult listEndpoints(
            @Valid @RequestBody GitLabRepositoryEndpointListRequest request
    ) {
        return gitLabRepositoryEndpointService.listEndpoints(request);
    }

    @PostMapping("/endpoint-use-case-context")
    public GitLabEndpointUseCaseContextResult buildEndpointUseCaseContext(
            @Valid @RequestBody GitLabEndpointUseCaseContextApiRequest request
    ) {
        return gitLabEndpointUseCaseContextService.buildContext(
                request.group(),
                request.branch(),
                request.toUseCaseRequest()
        );
    }

    @PostMapping("/java-method-use-case-context")
    public GitLabJavaMethodUseCaseContextResult buildJavaMethodUseCaseContext(
            @Valid @RequestBody GitLabJavaMethodUseCaseContextApiRequest request
    ) {
        return gitLabJavaMethodUseCaseContextService.buildContext(
                request.group(),
                request.branch(),
                request.toUseCaseRequest()
        );
    }

    @PostMapping("/files/by-path")
    public GitLabRepositoryFilesByPathApiResponse readFilesByPath(
            @Valid @RequestBody GitLabRepositoryFilesByPathApiRequest request
    ) {
        return gitLabRepositoryFilesByPathApiService.readFiles(request);
    }

    @PostMapping("/java-method-slice")
    public GitLabJavaMethodSliceResponse readJavaMethodSlice(
            @Valid @RequestBody GitLabJavaMethodSliceRequest request
    ) {
        return gitLabJavaMethodSliceService.readMethodSlice(request);
    }

    @PostMapping("/openapi-endpoint-slice")
    public GitLabOpenApiEndpointSliceResponse readOpenApiEndpointSlice(
            @Valid @RequestBody GitLabOpenApiEndpointSliceRequest request
    ) {
        return gitLabOpenApiEndpointSliceService.readEndpointSlice(request);
    }

}
