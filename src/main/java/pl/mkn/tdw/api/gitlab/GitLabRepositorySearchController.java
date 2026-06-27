package pl.mkn.tdw.api.gitlab;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointListRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointListResult;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointService;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchResponse;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchService;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceRequest;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceResponse;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceService;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceRequest;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceResponse;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceService;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseContextResult;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseContextService;

@RestController
@RequestMapping("/api/gitlab/repository")
@RequiredArgsConstructor
public class GitLabRepositorySearchController {

    private final GitLabRepositorySearchService gitLabRepositorySearchService;
    private final GitLabRepositoryEndpointService gitLabRepositoryEndpointService;
    private final GitLabEndpointUseCaseContextService gitLabEndpointUseCaseContextService;
    private final GitLabRepositoryFilesByPathApiService gitLabRepositoryFilesByPathApiService;
    private final GitLabJavaMethodSliceService gitLabJavaMethodSliceService;
    private final GitLabOpenApiEndpointSliceService gitLabOpenApiEndpointSliceService;

    @PostMapping("/search")
    public GitLabRepositorySearchResponse search(@Valid @RequestBody GitLabRepositorySearchRequest request) {
        return gitLabRepositorySearchService.search(request);
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
