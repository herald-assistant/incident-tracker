package pl.mkn.tdw.agenttools.gitlab.mcp;

import lombok.extern.slf4j.Slf4j;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseStart;
import com.github.javaparser.Providers;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileMetadata;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointListRequest;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryEndpointService;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchQuery;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceRequest;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceMethodSelector;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceResponse;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceService;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceRequest;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceResponse;
import pl.mkn.tdw.integrations.gitlab.openapi.GitLabOpenApiEndpointSliceService;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseContextRequest;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseContextService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabBuildEndpointUseCaseContextToolResponse;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFileChunkRequest;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFileChunkResult;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFileContentResult;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFindClassReferencesToolResponse;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFindFlowContextToolResponse;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFlowContextCandidate;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabFlowContextGroup;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabJavaConstructorSummary;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabJavaFieldSummary;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabJavaMethodSummary;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabJavaTypeSummary;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabListAvailableRepositoriesToolResponse;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabListRepositoryEndpointsToolResponse;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFileChunksToolResponse;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFileChunkToolResponse;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFileOutlineToolResponse;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFileToolResponse;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabReadRepositoryFilesByPathToolResponse;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabSearchRepositoryCandidatesToolResponse;
import pl.mkn.tdw.agenttools.gitlab.mcp.GitLabToolDtos.GitLabToolScope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.FIND_CLASS_REFERENCES;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.FIND_FLOW_CONTEXT;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.LIST_AVAILABLE_REPOSITORIES;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.LIST_REPOSITORY_ENDPOINTS;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE_CHUNK;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILE_OUTLINE;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_REPOSITORY_FILES_BY_PATH;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_JAVA_METHOD_SLICE;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES;

@Component
@Slf4j
public class GitLabMcpTools {

    private static final int DEFAULT_MAX_CHARACTERS = 4_000;
    private static final int DEFAULT_OUTLINE_MAX_CHARACTERS = 30_000;
    private static final int DEFAULT_MAX_TOTAL_CHUNK_CHARACTERS = 20_000;
    private static final int DEFAULT_MAX_TOTAL_FILE_CHARACTERS = 60_000;
    private static final int DEFAULT_MAX_FILES_PER_ROLE = 5;
    private static final int MAX_FILES_PER_ROLE = 10;
    private static final int MAX_BATCH_CHUNKS = 8;
    private static final int MAX_BATCH_FILES_BY_PATH = 100;
    private static final int MAX_IMPORTS = 40;
    private static final int MAX_TYPE_SUMMARIES = 20;
    private static final int MAX_FIELD_SUMMARIES = 80;
    private static final int MAX_CONSTRUCTOR_SUMMARIES = 40;
    private static final int MAX_METHOD_SUMMARIES = 80;
    private static final int MAX_RECOMMENDED_NEXT_READS = 8;
    private static final int PREVIEW_MAX_CHARACTERS = 200;

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?[\\w.*]+\\s*;");
    private final GitLabRepositoryPort gitLabRepositoryPort;
    private final OperationalContextPort operationalContextPort;
    private final GitLabRepositoryEndpointService gitLabRepositoryEndpointService;
    private final GitLabEndpointUseCaseContextService gitLabEndpointUseCaseContextService;
    private final GitLabJavaMethodSliceService gitLabJavaMethodSliceService;
    private final GitLabOpenApiEndpointSliceService gitLabOpenApiEndpointSliceService;
    private final GitLabToolScopeResolver scopeResolver;

    @Autowired
    public GitLabMcpTools(
            GitLabRepositoryPort gitLabRepositoryPort,
            OperationalContextPort operationalContextPort,
            GitLabRepositoryEndpointService gitLabRepositoryEndpointService,
            GitLabEndpointUseCaseContextService gitLabEndpointUseCaseContextService,
            GitLabJavaMethodSliceService gitLabJavaMethodSliceService,
            GitLabOpenApiEndpointSliceService gitLabOpenApiEndpointSliceService,
            GitLabProperties gitLabProperties
    ) {
        this.gitLabRepositoryPort = gitLabRepositoryPort;
        this.operationalContextPort = operationalContextPort;
        this.gitLabRepositoryEndpointService = gitLabRepositoryEndpointService;
        this.gitLabEndpointUseCaseContextService = gitLabEndpointUseCaseContextService;
        this.gitLabJavaMethodSliceService = gitLabJavaMethodSliceService;
        this.gitLabOpenApiEndpointSliceService = gitLabOpenApiEndpointSliceService;
        this.scopeResolver = new GitLabToolScopeResolver(gitLabProperties, operationalContextPort);
    }

    public GitLabMcpTools(
            GitLabRepositoryPort gitLabRepositoryPort,
            OperationalContextPort operationalContextPort,
            GitLabRepositoryEndpointService gitLabRepositoryEndpointService,
            GitLabProperties gitLabProperties
    ) {
        this(
                gitLabRepositoryPort,
                operationalContextPort,
                gitLabRepositoryEndpointService,
                defaultEndpointUseCaseContextService(gitLabRepositoryPort, gitLabRepositoryEndpointService),
                new GitLabJavaMethodSliceService(gitLabRepositoryPort),
                new GitLabOpenApiEndpointSliceService(gitLabRepositoryPort, new ObjectMapper()),
                gitLabProperties
        );
    }

    public GitLabMcpTools(
            GitLabRepositoryPort gitLabRepositoryPort,
            OperationalContextPort operationalContextPort,
            GitLabRepositoryEndpointService gitLabRepositoryEndpointService
    ) {
        this(gitLabRepositoryPort, operationalContextPort, gitLabRepositoryEndpointService, new GitLabProperties());
    }

    public GitLabMcpTools(
            GitLabRepositoryPort gitLabRepositoryPort,
            OperationalContextPort operationalContextPort,
            GitLabProperties gitLabProperties
    ) {
        this(
                gitLabRepositoryPort,
                operationalContextPort,
                new GitLabRepositoryEndpointService(gitLabRepositoryPort),
                gitLabProperties
        );
    }

    public GitLabMcpTools(
            GitLabRepositoryPort gitLabRepositoryPort,
            OperationalContextPort operationalContextPort
    ) {
        this(gitLabRepositoryPort, operationalContextPort, new GitLabProperties());
    }

    public GitLabMcpTools(GitLabRepositoryPort gitLabRepositoryPort, GitLabProperties gitLabProperties) {
        this(gitLabRepositoryPort, ignored -> emptyOperationalContextCatalog(), gitLabProperties);
    }

    public GitLabMcpTools(GitLabRepositoryPort gitLabRepositoryPort) {
        this(gitLabRepositoryPort, new GitLabProperties());
    }

    private static GitLabEndpointUseCaseContextService defaultEndpointUseCaseContextService(
            GitLabRepositoryPort gitLabRepositoryPort,
            GitLabRepositoryEndpointService gitLabRepositoryEndpointService
    ) {
        return GitLabEndpointUseCaseContextService.createDefault(gitLabRepositoryPort, gitLabRepositoryEndpointService);
    }

    private GitLabToolScope scope(
            String projectName,
            String applicationName,
            String branchRef,
            ToolContext toolContext
    ) {
        return scopeResolver.resolve(branchRef, projectName, applicationName, toolContext);
    }

    private String firstProjectName(List<String> projectNames) {
        return defaultList(projectNames).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private String firstChunkProjectName(List<GitLabFileChunkRequest> chunks) {
        return defaultList(chunks).stream()
                .filter(chunk -> chunk != null && StringUtils.hasText(chunk.projectName()))
                .map(chunk -> chunk.projectName().trim())
                .findFirst()
                .orElse(null);
    }

    @Tool(
            name = LIST_AVAILABLE_REPOSITORIES,
            description = """
                    Lists GitLab repositories registered in operational context and available in the resolved GitLab group.
                    Use this when projectName or GitLab path is unknown and logs, traces, code, comments, package names,
                    system names, module names, endpoints or bounded contexts mention another application or repository.
                    Prefer returned codeSearchScopes when a semantic target maps to multiple repositories; use all projectName
                    values from the matching scope as inputs for GitLab search, flow context and read tools.
                    Pass branchRef explicitly from the prompt, an artifact or a previous tool result. The GitLab group is
                    resolved from projectName/applicationName via operational context or from application configuration.
                    """
    )
    public GitLabListAvailableRepositoriesToolResponse listAvailableRepositories(
            @ToolParam(required = false, description = "Application/system name from prompt or operational context, for example crm-service.")
            String applicationName,
            @ToolParam(description = "Git branch/ref from prompt, artifact or previous tool result.")
            String branchRef,
            @ToolParam(required = false, description = "Krotki powod po polsku: dlaczego model potrzebuje katalogu repozytoriow.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = scope(null, applicationName, branchRef, toolContext);

        log.info(
                "Tool request [{}] runReference={} group={} branch={} applicationName={} analysisRunId={} copilotSessionId={} toolCallId={}",
                LIST_AVAILABLE_REPOSITORIES,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId()
        );

        var catalog = operationalContextPort.loadContext(new OperationalContextQuery(
                Set.of(OperationalContextEntryType.REPOSITORY),
                List.of(),
                false
        ));
        var repositories = GitLabAvailableRepositoryMapper.fromCatalog(scope.group(), catalog);
        var codeSearchScopes = GitLabAvailableRepositoryMapper.codeSearchScopesFromCatalog(scope.group(), catalog);

        log.info(
                "Tool result [{}] runReference={} group={} branch={} applicationName={} repositoryCount={} codeSearchScopeCount={} projectNames={}",
                LIST_AVAILABLE_REPOSITORIES,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                repositories.size(),
                codeSearchScopes.size(),
                abbreviateList(repositories.stream()
                        .map(repository -> repository.projectName())
                        .toList())
        );

        return new GitLabListAvailableRepositoriesToolResponse(
                scope.group(),
                scope.branch(),
                repositories,
                codeSearchScopes
        );
    }

    @Tool(
            name = LIST_REPOSITORY_ENDPOINTS,
            description = """
                    Lists Spring MVC HTTP endpoints declared by RestController/Controller classes in a GitLab repository.
                    Provide projectName from gitlab_list_available_repositories
                    or another grounded GitLab result. Use endpointPathPrefix/httpMethod to narrow the inventory when the user asks
                    about a concrete endpoint family. The result returns endpointId, HTTP method/path, controller class/method,
                    file path, line range, request/response type hints and suggested next reads.
                    """
    )
    public GitLabListRepositoryEndpointsToolResponse listRepositoryEndpoints(
            @ToolParam(description = "GitLab project path inside the resolved GitLab group.")
            String projectName,
            @ToolParam(description = "Git branch/ref from prompt, artifact or previous tool result.")
            String branchRef,
            @ToolParam(required = false, description = "Application/system name from prompt or operational context, used to validate repository scope.")
            String applicationName,
            @ToolParam(required = false, description = "Optional endpoint path prefix, for example /api/orders.")
            String endpointPathPrefix,
            @ToolParam(required = false, description = "Optional HTTP method filter, for example GET, POST, PUT or DELETE.")
            String httpMethod,
            @ToolParam(required = false, description = "Maximum Java source files to scan. Defaults to backend limit and is capped by the server.")
            Integer maxScannedFiles,
            @ToolParam(required = false, description = "Krotki powod po polsku: w jakim celu model listuje endpointy repozytorium.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = scope(projectName, applicationName, branchRef, toolContext);

        log.info(
                "Tool request [{}] runReference={} group={} branch={} applicationName={} analysisRunId={} copilotSessionId={} toolCallId={} projectName={} endpointPathPrefix={} httpMethod={} maxScannedFiles={}",
                LIST_REPOSITORY_ENDPOINTS,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                projectName,
                endpointPathPrefix,
                httpMethod,
                maxScannedFiles
        );

        var result = gitLabRepositoryEndpointService.listEndpoints(new GitLabRepositoryEndpointListRequest(
                scope.group(),
                projectName,
                scope.branch(),
                endpointPathPrefix,
                httpMethod,
                maxScannedFiles
        ));

        log.info(
                "Tool result [{}] runReference={} group={} branch={} applicationName={} projectName={} endpointCount={} candidateFileCount={} scannedFileCount={} scannedFileLimitReached={}",
                LIST_REPOSITORY_ENDPOINTS,
                scope.runReference(),
                result.group(),
                result.branch(),
                scope.applicationName(),
                result.projectName(),
                result.endpoints().size(),
                result.candidateFileCount(),
                result.scannedFileCount(),
                result.scannedFileLimitReached()
        );

        return new GitLabListRepositoryEndpointsToolResponse(
                result.group(),
                result.projectName(),
                result.branch(),
                result.endpointPathPrefix(),
                result.httpMethod(),
                result.candidateFileCount(),
                result.scannedFileCount(),
                result.scannedFileLimitReached(),
                result.endpoints(),
                result.limitations()
        );
    }

    @Tool(
            name = BUILD_ENDPOINT_USE_CASE_CONTEXT,
            description = """
                    Builds a compact use-case context for one concrete HTTP endpoint in the resolved GitLab group and explicit branchRef.
                    Use after gitlab_list_repository_endpoints when endpointId is known, or provide httpMethod + endpointPath when the
                    endpoint is uniquely identifiable. The result returns candidate file paths, roles, symbols, direct relations,
                    unresolved references, limitations and suggested next reads for focused code fetching.
                    """
    )
    public GitLabBuildEndpointUseCaseContextToolResponse buildEndpointUseCaseContext(
            @ToolParam(description = "GitLab project path inside the resolved GitLab group.")
            String projectName,
            @ToolParam(description = "Git branch/ref from prompt, artifact or previous tool result.")
            String branchRef,
            @ToolParam(required = false, description = "Application/system name from prompt or operational context, used to validate repository scope.")
            String applicationName,
            @ToolParam(required = false, description = "Exact endpointId returned by gitlab_list_repository_endpoints.")
            String endpointId,
            @ToolParam(required = false, description = "HTTP method when endpointId is unknown, for example GET, POST, PUT or DELETE.")
            String httpMethod,
            @ToolParam(required = false, description = "Endpoint path when endpointId is unknown, for example /api/orders/{orderId}.")
            String endpointPath,
            @ToolParam(required = false, description = "Maximum traversal depth. Defaults to backend limit and is capped by the server.")
            Integer maxDepth,
            @ToolParam(required = false, description = "Maximum returned files. Defaults to backend limit and is capped by the server.")
            Integer maxFiles,
            @ToolParam(required = false, description = "Krotki powod po polsku: w jakim celu model buduje kontekst endpointu.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = scope(projectName, applicationName, branchRef, toolContext);

        log.info(
                "Tool request [{}] runReference={} group={} branch={} applicationName={} analysisRunId={} copilotSessionId={} toolCallId={} projectName={} endpointId={} httpMethod={} endpointPath={} maxDepth={} maxFiles={}",
                BUILD_ENDPOINT_USE_CASE_CONTEXT,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                projectName,
                endpointId,
                httpMethod,
                endpointPath,
                maxDepth,
                maxFiles
        );

        var result = gitLabEndpointUseCaseContextService.buildContext(
                scope.group(),
                scope.branch(),
                new GitLabEndpointUseCaseContextRequest(
                        projectName,
                        endpointId,
                        httpMethod,
                        endpointPath,
                        maxDepth,
                        maxFiles
                )
        );
        var response = GitLabBuildEndpointUseCaseContextToolResponse.from(result);

        log.info(
                "Tool result [{}] runReference={} group={} branch={} applicationName={} projectName={} endpointResolved={} fileCount={} relationCount={} unresolvedCount={} suggestedNextReadCount={} confidence={}",
                BUILD_ENDPOINT_USE_CASE_CONTEXT,
                scope.runReference(),
                response.group(),
                response.branch(),
                scope.applicationName(),
                response.projectName(),
                response.endpoint() != null,
                response.files().size(),
                response.relations().size(),
                response.unresolved().size(),
                response.suggestedNextReads().size(),
                response.confidence()
        );

        return response;
    }

    @Tool(
            name = SEARCH_REPOSITORY_CANDIDATES,
            description = """
                    Search GitLab repository files in the resolved GitLab group and explicit branchRef using candidate project names,
                    operation names, class names, method names, entity names, exception names and keywords inferred from logs,
                    traces or prior analysis. Use this when the affected project/file is unclear or when broader repository
                    candidates are needed before focused reads.
                    """
    )
    public GitLabSearchRepositoryCandidatesToolResponse searchRepositoryCandidates(
            @ToolParam(required = false, description = "One or more candidate GitLab project paths inside the resolved GitLab group. Relative subgroup segments are allowed.")
            List<String> projectNames,
            @ToolParam(description = "Git branch/ref from prompt, artifact or previous tool result.")
            String branchRef,
            @ToolParam(required = false, description = "Application/system name from prompt or operational context, used to validate repository scope.")
            String applicationName,
            @ToolParam(required = false, description = "Operation names inferred from traces.")
            List<String> operationNames,
            @ToolParam(required = false, description = "Keywords inferred from logs, traces, code identifiers or the current hypothesis.")
            List<String> keywords,
            @ToolParam(required = false, description = "Krotki powod po polsku: w jakim celu model wywoluje to narzedzie.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = scope(firstProjectName(projectNames), applicationName, branchRef, toolContext);
        var safeProjectNames = defaultList(projectNames);
        var safeOperationNames = defaultList(operationNames);
        var safeKeywords = defaultList(keywords);

        log.info(
                "Tool request [{}] runReference={} group={} branch={} applicationName={} analysisRunId={} copilotSessionId={} toolCallId={} projectNames={} operationNames={} keywords={}",
                SEARCH_REPOSITORY_CANDIDATES,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                abbreviateList(safeProjectNames),
                abbreviateList(safeOperationNames),
                abbreviateList(safeKeywords)
        );

        var query = new GitLabRepositorySearchQuery(
                null,
                scope.group(),
                scope.branch(),
                safeProjectNames,
                safeOperationNames,
                safeKeywords
        );
        var candidates = gitLabRepositoryPort.searchCandidateFiles(query);

        log.info(
                "Tool result [{}] runReference={} group={} branch={} applicationName={} candidateCount={} candidatePaths={}",
                SEARCH_REPOSITORY_CANDIDATES,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                candidates.size(),
                abbreviateList(candidates.stream()
                        .map(candidate -> candidate.projectName() + ":" + candidate.filePath())
                        .toList())
        );

        return new GitLabSearchRepositoryCandidatesToolResponse(candidates);
    }

    @Tool(
            name = READ_REPOSITORY_FILE,
            description = """
                    Read a file from the GitLab repository in the resolved GitLab group and explicit branchRef.
                    Use only when full class/file context is necessary. Prefer outline/chunk/chunks before full file reads for large files.
                    """
    )
    public GitLabReadRepositoryFileToolResponse readRepositoryFile(
            @ToolParam(description = "GitLab project path inside the resolved GitLab group.")
            String projectName,
            @ToolParam(description = "Git branch/ref from prompt, artifact or previous tool result.")
            String branchRef,
            @ToolParam(required = false, description = "Application/system name from prompt or operational context, used to validate repository scope.")
            String applicationName,
            @ToolParam(description = "Repository file path.")
            String filePath,
            @ToolParam(required = false, description = "Maximum number of characters to return. Defaults to 4000.")
            Integer maxCharacters,
            @ToolParam(required = false, description = "Krotki powod po polsku: w jakim celu model czyta ten plik.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = scope(projectName, applicationName, branchRef, toolContext);
        var effectiveMaxCharacters = normalizePositiveLimit(maxCharacters, DEFAULT_MAX_CHARACTERS);

        log.info(
                "Tool request [{}] runReference={} group={} branch={} applicationName={} analysisRunId={} copilotSessionId={} toolCallId={} projectName={} filePath={} maxCharacters={}",
                READ_REPOSITORY_FILE,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                projectName,
                filePath,
                effectiveMaxCharacters
        );

        var fileContent = gitLabRepositoryPort.readFile(
                scope.group(),
                projectName,
                scope.branch(),
                filePath,
                effectiveMaxCharacters
        );

        log.info(
                "Tool result [{}] runReference={} group={} branch={} applicationName={} projectName={} filePath={} returnedCharacters={} truncated={}",
                READ_REPOSITORY_FILE,
                scope.runReference(),
                responseGroup(fileContent, scope.group()),
                responseBranch(fileContent, scope.branch()),
                scope.applicationName(),
                responseProjectName(fileContent, projectName),
                responseFilePath(fileContent, filePath),
                fileContent != null && fileContent.content() != null ? fileContent.content().length() : 0,
                fileContent != null && fileContent.truncated()
        );

        return new GitLabReadRepositoryFileToolResponse(
                responseGroup(fileContent, scope.group()),
                responseProjectName(fileContent, projectName),
                responseBranch(fileContent, scope.branch()),
                responseFilePath(fileContent, filePath),
                fileContent != null ? fileContent.content() : null,
                fileContent != null && fileContent.truncated()
        );
    }

    @Tool(
            name = READ_JAVA_METHOD_SLICE,
            description = """
                    Read a focused Java class slice for one or more methods from a GitLab repository in the resolved GitLab group and explicit branchRef.
                    Provide methodSelectors with methodName and optional lineStart. When lineStart is omitted, all overloads with that
                    method name are returned. The tool parses the Java file and returns package, relevant imports, the declaring class
                    header, fields used by returned methods, selected methods and optional private local helper methods. Unrelated
                    fields/methods are replaced with omission markers. Prefer this over full file reads or raw chunks when method-level context is enough.
                    """
    )
    public GitLabJavaMethodSliceResponse readJavaMethodSlice(
            @ToolParam(description = "GitLab project path inside the resolved GitLab group.")
            String projectName,
            @ToolParam(description = "Git branch/ref from prompt, artifact or previous tool result.")
            String branchRef,
            @ToolParam(required = false, description = "Application/system name from prompt or operational context, used to validate repository scope.")
            String applicationName,
            @ToolParam(description = "Repository Java file path.")
            String filePath,
            @ToolParam(required = false, description = "Optional declaring class/simple type name when the file contains nested or multiple types.")
            String declaringTypeName,
            @ToolParam(description = "Methods to slice. Each selector requires methodName and can optionally provide lineStart to narrow to one overload.")
            List<GitLabJavaMethodSliceMethodSelector> methodSelectors,
            @ToolParam(required = false, description = "Include private helper methods called by returned methods. Defaults to true.")
            Boolean includeDirectPrivateHelpers,
            @ToolParam(required = false, description = "Include fields used by returned methods/helper methods. Defaults to true.")
            Boolean includeRelevantFields,
            @ToolParam(required = false, description = "Include imports relevant to returned code. Defaults to true.")
            Boolean includeRelevantImports,
            @ToolParam(required = false, description = "Maximum returned characters. Defaults to 8000 and is capped by the server.")
            Integer maxCharacters,
            @ToolParam(required = false, description = "Krotki powod po polsku: w jakim celu model czyta wycinek metody.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = scope(projectName, applicationName, branchRef, toolContext);

        log.info(
                "Tool request [{}] runReference={} group={} branch={} applicationName={} analysisRunId={} copilotSessionId={} toolCallId={} projectName={} filePath={} declaringTypeName={} methodSelectors={} maxCharacters={}",
                READ_JAVA_METHOD_SLICE,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                projectName,
                filePath,
                declaringTypeName,
                methodSelectors,
                maxCharacters
        );

        var response = gitLabJavaMethodSliceService.readMethodSlice(new GitLabJavaMethodSliceRequest(
                scope.group(),
                projectName,
                scope.branch(),
                filePath,
                declaringTypeName,
                methodSelectors,
                includeDirectPrivateHelpers,
                includeRelevantFields,
                includeRelevantImports,
                maxCharacters
        ));

        log.info(
                "Tool result [{}] runReference={} group={} branch={} applicationName={} projectName={} filePath={} status={} returnedCharacters={} truncated={} candidateCount={} limitationCount={}",
                READ_JAVA_METHOD_SLICE,
                scope.runReference(),
                response.group(),
                response.branch(),
                scope.applicationName(),
                response.projectName(),
                response.filePath(),
                response.status(),
                response.returnedCharacters(),
                response.truncated(),
                response.candidates().size(),
                response.limitations().size()
        );

        return response;
    }

    @Tool(
            name = READ_OPENAPI_ENDPOINT_SLICE,
            description = """
                    Read a focused OpenAPI/Swagger YAML slice for one concrete endpoint operation from a GitLab repository.
                    The tool validates that the file is YAML, parses the OpenAPI/Swagger manifest, checks supported version
                    (OpenAPI 3.x or Swagger 2.0), filters paths to the requested httpMethod + endpointPath, and returns only
                    the operation plus locally referenced schemas/components up to schemaDepth. Prefer this over reading a
                    full OpenAPI YAML file when endpoint contract details are needed.
                    """
    )
    public GitLabOpenApiEndpointSliceResponse readOpenApiEndpointSlice(
            @ToolParam(description = "GitLab project path inside the resolved GitLab group.")
            String projectName,
            @ToolParam(description = "Git branch/ref from prompt, artifact or previous tool result.")
            String branchRef,
            @ToolParam(required = false, description = "Application/system name from prompt or operational context, used to validate repository scope.")
            String applicationName,
            @ToolParam(description = "Repository OpenAPI YAML file path.")
            String filePath,
            @ToolParam(description = "HTTP method, for example GET, POST, PUT or DELETE.")
            String httpMethod,
            @ToolParam(description = "Endpoint path from prompt, artifact or previous tool result.")
            String endpointPath,
            @ToolParam(required = false, description = "Include local $ref schemas/components used by this operation. Defaults to true.")
            Boolean includeReferencedSchemas,
            @ToolParam(required = false, description = "Maximum local $ref traversal depth. Defaults to 2 and is capped by the server.")
            Integer schemaDepth,
            @ToolParam(required = false, description = "Maximum returned characters. Defaults to 20000 and is capped by the server.")
            Integer maxCharacters,
            @ToolParam(required = false, description = "Krotki powod po polsku: w jakim celu model czyta kontrakt OpenAPI endpointu.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = scope(projectName, applicationName, branchRef, toolContext);

        log.info(
                "Tool request [{}] runReference={} group={} branch={} applicationName={} analysisRunId={} copilotSessionId={} toolCallId={} projectName={} filePath={} httpMethod={} endpointPath={} schemaDepth={} maxCharacters={}",
                READ_OPENAPI_ENDPOINT_SLICE,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                projectName,
                filePath,
                httpMethod,
                endpointPath,
                schemaDepth,
                maxCharacters
        );

        var response = gitLabOpenApiEndpointSliceService.readEndpointSlice(new GitLabOpenApiEndpointSliceRequest(
                scope.group(),
                projectName,
                scope.branch(),
                filePath,
                httpMethod,
                endpointPath,
                includeReferencedSchemas,
                schemaDepth,
                maxCharacters
        ));

        log.info(
                "Tool result [{}] runReference={} group={} branch={} applicationName={} projectName={} filePath={} status={} matchedPath={} returnedCharacters={} truncated={} limitationCount={}",
                READ_OPENAPI_ENDPOINT_SLICE,
                scope.runReference(),
                response.group(),
                response.branch(),
                scope.applicationName(),
                response.projectName(),
                response.filePath(),
                response.status(),
                response.matchedPath(),
                response.returnedCharacters(),
                response.truncated(),
                response.limitations().size()
        );

        return response;
    }

    @Tool(
            name = READ_REPOSITORY_FILES_BY_PATH,
            description = """
                    Read several full files from one GitLab repository by exact repository file paths in the resolved GitLab group and explicit branchRef.
                    Use after gitlab_build_endpoint_use_case_context when the model has a grounded list of relevant files and needs their source
                    content for functional/technical endpoint interpretation. The server enforces maximum files and total characters.
                    """
    )
    public GitLabReadRepositoryFilesByPathToolResponse readRepositoryFilesByPath(
            @ToolParam(description = "GitLab project path inside the resolved GitLab group.")
            String projectName,
            @ToolParam(description = "Git branch/ref from prompt, artifact or previous tool result.")
            String branchRef,
            @ToolParam(required = false, description = "Application/system name from prompt or operational context, used to validate repository scope.")
            String applicationName,
            @ToolParam(description = "Exact repository file paths. Maximum 100 distinct paths are processed.")
            List<String> filePaths,
            @ToolParam(required = false, description = "Maximum characters returned per file. Defaults to 4000.")
            Integer maxCharactersPerFile,
            @ToolParam(required = false, description = "Maximum total characters returned across all files. Defaults to 60000.")
            Integer maxTotalCharacters,
            @ToolParam(required = false, description = "Krotki powod po polsku: w jakim celu model czyta te pliki.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = scope(projectName, applicationName, branchRef, toolContext);
        var requestedFilePaths = normalizeRepositoryFilePaths(filePaths, projectName);
        var processedFilePaths = requestedFilePaths.stream()
                .limit(MAX_BATCH_FILES_BY_PATH)
                .toList();
        var effectiveMaxCharactersPerFile = normalizePositiveLimit(maxCharactersPerFile, DEFAULT_MAX_CHARACTERS);
        var effectiveMaxTotalCharacters = normalizePositiveLimit(maxTotalCharacters, DEFAULT_MAX_TOTAL_FILE_CHARACTERS);

        log.info(
                "Tool request [{}] runReference={} group={} branch={} applicationName={} analysisRunId={} copilotSessionId={} toolCallId={} projectName={} requestedFileCount={} processedFileCount={} filePaths={} maxCharactersPerFile={} maxTotalCharacters={}",
                READ_REPOSITORY_FILES_BY_PATH,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                projectName,
                requestedFilePaths.size(),
                processedFilePaths.size(),
                abbreviateList(processedFilePaths),
                effectiveMaxCharactersPerFile,
                effectiveMaxTotalCharacters
        );

        var remainingCharacters = effectiveMaxTotalCharacters;
        var totalReturnedCharacters = 0;
        var returnedFileCount = 0;
        var failedFileCount = 0;
        var totalCharacterLimitReached = false;
        var results = new ArrayList<GitLabFileContentResult>();

        for (var requestedPath : processedFilePaths) {
            if (remainingCharacters <= 0) {
                totalCharacterLimitReached = true;
                break;
            }

            var effectiveFileMaxCharacters = Math.min(effectiveMaxCharactersPerFile, remainingCharacters);
            try {
                var fileContent = gitLabRepositoryPort.readFile(
                        scope.group(),
                        projectName,
                        scope.branch(),
                        requestedPath,
                        effectiveFileMaxCharacters
                );
                var content = fileContent != null ? fileContent.content() : null;
                var returnedCharacters = safeLength(content);
                var inferredRole = inferRole(
                        responseFilePath(fileContent, requestedPath),
                        content
                );
                var metadata = readFileMetadata(scope, projectName, responseFilePath(fileContent, requestedPath));

                results.add(new GitLabFileContentResult(
                        responseGroup(fileContent, scope.group()),
                        responseProjectName(fileContent, projectName),
                        responseBranch(fileContent, scope.branch()),
                        responseFilePath(fileContent, requestedPath),
                        content,
                        fileContent != null && fileContent.truncated(),
                        inferredRole,
                        returnedCharacters,
                        metadata.sizeBytes(),
                        metadata.contentSha256(),
                        metadata.blobId(),
                        metadata.commitId(),
                        metadata.lastCommitId(),
                        metadata.lastModifiedAt(),
                        metadata.status(),
                        metadata.error(),
                        null
                ));
                returnedFileCount++;
                totalReturnedCharacters += returnedCharacters;
                remainingCharacters -= returnedCharacters;
                if (remainingCharacters <= 0) {
                    totalCharacterLimitReached = true;
                }
            } catch (RuntimeException exception) {
                failedFileCount++;
                var error = toolErrorMessage(exception);
                log.warn(
                        "Tool partial failure [{}] runReference={} group={} branch={} applicationName={} projectName={} filePath={} reason={}",
                        READ_REPOSITORY_FILES_BY_PATH,
                        scope.runReference(),
                        scope.group(),
                        scope.branch(),
                        scope.applicationName(),
                        projectName,
                        requestedPath,
                        error
                );
                results.add(new GitLabFileContentResult(
                        scope.group(),
                        projectName,
                        scope.branch(),
                        requestedPath,
                        null,
                        false,
                        inferRole(requestedPath, null),
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "SKIPPED",
                        "File content read failed before metadata lookup.",
                        error
                ));
            }
        }

        log.info(
                "Tool result [{}] runReference={} group={} branch={} applicationName={} projectName={} requestedFileCount={} processedFileCount={} returnedFileCount={} failedFileCount={} totalReturnedCharacters={} fileCountTruncated={} totalCharacterLimitReached={}",
                READ_REPOSITORY_FILES_BY_PATH,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                projectName,
                requestedFilePaths.size(),
                results.size(),
                returnedFileCount,
                failedFileCount,
                totalReturnedCharacters,
                requestedFilePaths.size() > MAX_BATCH_FILES_BY_PATH,
                totalCharacterLimitReached
        );

        return new GitLabReadRepositoryFilesByPathToolResponse(
                scope.group(),
                projectName,
                scope.branch(),
                requestedFilePaths.size(),
                results.size(),
                returnedFileCount,
                failedFileCount,
                totalReturnedCharacters,
                requestedFilePaths.size() > MAX_BATCH_FILES_BY_PATH,
                totalCharacterLimitReached,
                List.copyOf(results)
        );
    }

    @Tool(
            name = READ_REPOSITORY_FILE_CHUNK,
            description = """
                    Read only a selected line range from a GitLab repository file in the resolved GitLab group and explicit branchRef.
                    Line numbers are 1-based and inclusive. Prefer this over full file reads when investigating a stack frame,
                    method, repository predicate, mapper, validator, or client call.
                    """
    )
    public GitLabReadRepositoryFileChunkToolResponse readRepositoryFileChunk(
            @ToolParam(description = "GitLab project path inside the resolved GitLab group.")
            String projectName,
            @ToolParam(description = "Git branch/ref from prompt, artifact or previous tool result.")
            String branchRef,
            @ToolParam(required = false, description = "Application/system name from prompt or operational context, used to validate repository scope.")
            String applicationName,
            @ToolParam(description = "Repository file path.")
            String filePath,
            @ToolParam(description = "First line to return. Uses 1-based inclusive numbering.")
            int startLine,
            @ToolParam(description = "Last line to return. Uses 1-based inclusive numbering.")
            int endLine,
            @ToolParam(required = false, description = "Maximum number of characters to return. Defaults to 4000.")
            Integer maxCharacters,
            @ToolParam(required = false, description = "Krotki powod po polsku: w jakim celu model czyta ten fragment pliku.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = scope(projectName, applicationName, branchRef, toolContext);
        var effectiveMaxCharacters = normalizePositiveLimit(maxCharacters, DEFAULT_MAX_CHARACTERS);

        log.info(
                "Tool request [{}] runReference={} group={} branch={} applicationName={} analysisRunId={} copilotSessionId={} toolCallId={} projectName={} filePath={} requestedStartLine={} requestedEndLine={} maxCharacters={}",
                READ_REPOSITORY_FILE_CHUNK,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                projectName,
                filePath,
                startLine,
                endLine,
                effectiveMaxCharacters
        );

        var fileChunk = gitLabRepositoryPort.readFileChunk(
                scope.group(),
                projectName,
                scope.branch(),
                filePath,
                startLine,
                endLine,
                effectiveMaxCharacters
        );

        log.info(
                "Tool result [{}] runReference={} group={} branch={} applicationName={} projectName={} filePath={} returnedStartLine={} returnedEndLine={} totalLines={} returnedCharacters={} truncated={}",
                READ_REPOSITORY_FILE_CHUNK,
                scope.runReference(),
                fileChunk != null ? fileChunk.group() : scope.group(),
                fileChunk != null ? fileChunk.branch() : scope.branch(),
                scope.applicationName(),
                fileChunk != null ? fileChunk.projectName() : projectName,
                fileChunk != null ? fileChunk.filePath() : filePath,
                fileChunk != null ? fileChunk.returnedStartLine() : 0,
                fileChunk != null ? fileChunk.returnedEndLine() : 0,
                fileChunk != null ? fileChunk.totalLines() : 0,
                fileChunk != null && fileChunk.content() != null ? fileChunk.content().length() : 0,
                fileChunk != null && fileChunk.truncated()
        );

        return new GitLabReadRepositoryFileChunkToolResponse(
                fileChunk != null ? fileChunk.group() : scope.group(),
                fileChunk != null ? fileChunk.projectName() : projectName,
                fileChunk != null ? fileChunk.branch() : scope.branch(),
                fileChunk != null ? fileChunk.filePath() : filePath,
                fileChunk != null ? fileChunk.requestedStartLine() : startLine,
                fileChunk != null ? fileChunk.requestedEndLine() : endLine,
                fileChunk != null ? fileChunk.returnedStartLine() : 0,
                fileChunk != null ? fileChunk.returnedEndLine() : 0,
                fileChunk != null ? fileChunk.totalLines() : 0,
                fileChunk != null ? fileChunk.content() : null,
                fileChunk != null && fileChunk.truncated()
        );
    }

    @Tool(
            name = READ_REPOSITORY_FILE_OUTLINE,
            description = """
                    Read a lightweight outline of a GitLab repository file in the resolved GitLab group and explicit branchRef:
                    package, imports summary, type summaries, field summaries, constructor summaries, method summaries and inferred file role.
                    Type, constructor, method and field annotations are attached to the element where they appear.
                    Use before reading a full file when the model needs to understand the class role in the broader flow without loading all code.
                    """
    )
    public GitLabReadRepositoryFileOutlineToolResponse readRepositoryFileOutline(
            @ToolParam(description = "GitLab project path inside the resolved GitLab group.")
            String projectName,
            @ToolParam(description = "Git branch/ref from prompt, artifact or previous tool result.")
            String branchRef,
            @ToolParam(required = false, description = "Application/system name from prompt or operational context, used to validate repository scope.")
            String applicationName,
            @ToolParam(description = "Repository file path.")
            String filePath,
            @ToolParam(required = false, description = "Maximum number of characters to read before extracting outline. Defaults to 30000.")
            Integer maxCharacters,
            @ToolParam(required = false, description = "Krotki powod po polsku: w jakim celu model sprawdza zarys tego pliku.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = scope(projectName, applicationName, branchRef, toolContext);
        var effectiveMaxCharacters = normalizePositiveLimit(maxCharacters, DEFAULT_OUTLINE_MAX_CHARACTERS);

        log.info(
                "Tool request [{}] runReference={} group={} branch={} applicationName={} analysisRunId={} copilotSessionId={} toolCallId={} projectName={} filePath={} maxCharacters={}",
                READ_REPOSITORY_FILE_OUTLINE,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                projectName,
                filePath,
                effectiveMaxCharacters
        );

        var fileContent = gitLabRepositoryPort.readFile(
                scope.group(),
                projectName,
                scope.branch(),
                filePath,
                effectiveMaxCharacters
        );
        var outline = buildOutline(fileContent != null ? fileContent.content() : null);
        var inferredRole = inferRole(filePath, fileContent != null ? fileContent.content() : null);

        log.info(
                "Tool result [{}] runReference={} group={} branch={} applicationName={} projectName={} filePath={} packageName={} importCount={} typeSummaryCount={} fieldSummaryCount={} constructorSummaryCount={} methodSummaryCount={} inferredRole={} truncated={}",
                READ_REPOSITORY_FILE_OUTLINE,
                scope.runReference(),
                responseGroup(fileContent, scope.group()),
                responseBranch(fileContent, scope.branch()),
                scope.applicationName(),
                responseProjectName(fileContent, projectName),
                responseFilePath(fileContent, filePath),
                outline.packageName(),
                outline.imports().size(),
                outline.typeSummaries().size(),
                outline.fieldSummaries().size(),
                outline.constructorSummaries().size(),
                outline.methodSummaries().size(),
                inferredRole,
                fileContent != null && fileContent.truncated()
        );

        return new GitLabReadRepositoryFileOutlineToolResponse(
                responseGroup(fileContent, scope.group()),
                responseProjectName(fileContent, projectName),
                responseBranch(fileContent, scope.branch()),
                responseFilePath(fileContent, filePath),
                outline.packageName(),
                outline.imports(),
                outline.typeSummaries(),
                outline.fieldSummaries(),
                outline.constructorSummaries(),
                outline.methodSummaries(),
                inferredRole,
                fileContent != null && fileContent.truncated()
        );
    }

    @Tool(
            name = READ_REPOSITORY_FILE_CHUNKS,
            description = """
                    Read several focused line ranges from GitLab files in the resolved GitLab group and explicit branchRef in one tool call.
                    Use when multiple directly related chunks are needed to explain the affected flow, for example service + repository + mapper
                    or listener + outbox handler + downstream client. The server enforces maximum chunks and total characters.
                    """
    )
    public GitLabReadRepositoryFileChunksToolResponse readRepositoryFileChunks(
            @ToolParam(description = "Focused chunk requests. Maximum 8 chunks are processed.")
            List<GitLabFileChunkRequest> chunks,
            @ToolParam(description = "Git branch/ref from prompt, artifact or previous tool result.")
            String branchRef,
            @ToolParam(required = false, description = "Application/system name from prompt or operational context, used to validate repository scope.")
            String applicationName,
            @ToolParam(required = false, description = "Maximum total characters returned across all chunks. Defaults to 20000.")
            Integer maxTotalCharacters,
            @ToolParam(required = false, description = "Krotki powod po polsku: w jakim celu model czyta te fragmenty plikow.")
            String reason,
            ToolContext toolContext
    ) {
        var safeChunks = defaultList(chunks);
        var scope = scope(firstChunkProjectName(safeChunks), applicationName, branchRef, toolContext);
        var processedChunks = safeChunks.stream()
                .filter(chunk -> chunk != null)
                .limit(MAX_BATCH_CHUNKS)
                .toList();
        var effectiveMaxTotalCharacters = normalizePositiveLimit(
                maxTotalCharacters,
                DEFAULT_MAX_TOTAL_CHUNK_CHARACTERS
        );

        log.info(
                "Tool request [{}] runReference={} group={} branch={} applicationName={} analysisRunId={} copilotSessionId={} toolCallId={} requestedChunkCount={} processedChunkCount={} chunkTargets={} maxTotalCharacters={}",
                READ_REPOSITORY_FILE_CHUNKS,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                safeChunks.size(),
                processedChunks.size(),
                abbreviateList(processedChunks.stream()
                        .map(chunk -> chunk.projectName() + ":" + chunk.filePath())
                        .toList()),
                effectiveMaxTotalCharacters
        );

        var remainingCharacters = effectiveMaxTotalCharacters;
        var totalCharacterLimitReached = false;
        var results = new ArrayList<GitLabFileChunkResult>();

        for (var chunk : processedChunks) {
            if (remainingCharacters <= 0) {
                totalCharacterLimitReached = true;
                break;
            }

            var requestedMaxCharacters = normalizePositiveLimit(chunk.maxCharacters(), DEFAULT_MAX_CHARACTERS);
            var effectiveChunkMaxCharacters = Math.min(requestedMaxCharacters, remainingCharacters);
            var fileChunk = gitLabRepositoryPort.readFileChunk(
                    scope.group(),
                    chunk.projectName(),
                    scope.branch(),
                    chunk.filePath(),
                    chunk.startLine(),
                    chunk.endLine(),
                    effectiveChunkMaxCharacters
            );
            var inferredRole = inferRole(
                    fileChunk != null ? fileChunk.filePath() : chunk.filePath(),
                    fileChunk != null ? fileChunk.content() : null
            );

            results.add(new GitLabFileChunkResult(
                    fileChunk != null ? fileChunk.group() : scope.group(),
                    fileChunk != null ? fileChunk.projectName() : chunk.projectName(),
                    fileChunk != null ? fileChunk.branch() : scope.branch(),
                    fileChunk != null ? fileChunk.filePath() : chunk.filePath(),
                    fileChunk != null ? fileChunk.requestedStartLine() : chunk.startLine(),
                    fileChunk != null ? fileChunk.requestedEndLine() : chunk.endLine(),
                    fileChunk != null ? fileChunk.returnedStartLine() : 0,
                    fileChunk != null ? fileChunk.returnedEndLine() : 0,
                    fileChunk != null ? fileChunk.totalLines() : 0,
                    fileChunk != null ? fileChunk.content() : null,
                    fileChunk != null && fileChunk.truncated(),
                    inferredRole
            ));

            remainingCharacters -= safeLength(fileChunk != null ? fileChunk.content() : null);
            if (remainingCharacters <= 0) {
                totalCharacterLimitReached = true;
            }
        }

        log.info(
                "Tool result [{}] runReference={} group={} branch={} applicationName={} chunkCount={} chunkCountTruncated={} totalCharacterLimitReached={}",
                READ_REPOSITORY_FILE_CHUNKS,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                results.size(),
                safeChunks.size() > MAX_BATCH_CHUNKS,
                totalCharacterLimitReached
        );

        return new GitLabReadRepositoryFileChunksToolResponse(
                scope.group(),
                scope.branch(),
                List.copyOf(results),
                safeChunks.size() > MAX_BATCH_CHUNKS,
                totalCharacterLimitReached
        );
    }

    @Tool(
            name = FIND_CLASS_REFERENCES,
            description = """
                    Finds files in the resolved GitLab group and explicit branchRef that declare, import or directly use a grounded class.
                    Use this when an exception, stacktrace or deterministic code evidence points to an entity, repository, DTO,
                    mapper, validator or client class and you need surrounding code to infer repository predicates, JPA/table hints,
                    direct relations or the broader flow before DB diagnostics.
                    """
    )
    public GitLabFindClassReferencesToolResponse findClassReferences(
            @ToolParam(required = false, description = "Candidate GitLab project paths inside the resolved GitLab group.")
            List<String> projectNames,
            @ToolParam(description = "Git branch/ref from prompt, artifact or previous tool result.")
            String branchRef,
            @ToolParam(required = false, description = "Application/system name from prompt or operational context, used to validate repository scope.")
            String applicationName,
            @ToolParam(description = "Grounded fully qualified or simple class name, for example pl.mkn.orders.domain.OrderEntity.")
            String className,
            @ToolParam(required = false, description = "Optional relation or mapping hints such as @Entity, @Table, repository method, JoinColumn, mappedBy, business key or exception name.")
            List<String> relatedHints,
            @ToolParam(required = false, description = "Operation names inferred from logs/traces.")
            List<String> operationNames,
            @ToolParam(required = false, description = "Maximum files per inferred role. Defaults to 5.")
            Integer maxFilesPerRole,
            @ToolParam(required = false, description = "Krotki powod po polsku: w jakim celu model szuka referencji tej klasy.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = scope(firstProjectName(projectNames), applicationName, branchRef, toolContext);
        var safeProjectNames = defaultList(projectNames);
        var safeOperationNames = defaultList(operationNames);
        var searchKeywords = deduplicate(joinLists(
                classReferenceKeywords(className),
                defaultList(relatedHints)
        ));
        var effectiveMaxFilesPerRole = normalizeMaxFilesPerRole(maxFilesPerRole);

        log.info(
                "Tool request [{}] runReference={} group={} branch={} applicationName={} analysisRunId={} copilotSessionId={} toolCallId={} className={} projectNames={} operationNames={} searchKeywords={} maxFilesPerRole={}",
                FIND_CLASS_REFERENCES,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                className,
                abbreviateList(safeProjectNames),
                abbreviateList(safeOperationNames),
                abbreviateList(searchKeywords),
                effectiveMaxFilesPerRole
        );

        var candidates = gitLabRepositoryPort.searchCandidateFiles(new GitLabRepositorySearchQuery(
                null,
                scope.group(),
                scope.branch(),
                safeProjectNames,
                safeOperationNames,
                searchKeywords
        ));
        var flowCandidates = toFlowContextCandidates(candidates);
        var groups = groupFlowCandidates(flowCandidates, effectiveMaxFilesPerRole);
        var recommendedNextReads = recommendedNextReads(flowCandidates);

        log.info(
                "Tool result [{}] runReference={} group={} branch={} applicationName={} candidateCount={} groupCount={} recommendedNextReadsCount={}",
                FIND_CLASS_REFERENCES,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                flowCandidates.size(),
                groups.size(),
                recommendedNextReads.size()
        );

        return new GitLabFindClassReferencesToolResponse(
                scope.group(),
                scope.branch(),
                className,
                searchKeywords,
                groups,
                recommendedNextReads
        );
    }

    @Tool(
            name = FIND_FLOW_CONTEXT,
            description = """
                    Finds a small set of directly related files in the resolved GitLab group and explicit branchRef that explain the functional
                    or technical flow around a failing class, method, entity, repository method, endpoint, queue, message type or integration keyword.
                    Returns grouped candidates by likely role: entrypoint, service/orchestrator, repository, mapper, validator,
                    downstream client, listener, scheduler, outbox/event handler, entity or configuration.
                    Use when the local error is known but the broader affected function is not yet clear.
                    """
    )
    public GitLabFindFlowContextToolResponse findFlowContext(
            @ToolParam(required = false, description = "Candidate GitLab project paths inside the resolved GitLab group.")
            List<String> projectNames,
            @ToolParam(description = "Git branch/ref from prompt, artifact or previous tool result.")
            String branchRef,
            @ToolParam(required = false, description = "Application/system name from prompt or operational context, used to validate repository scope.")
            String applicationName,
            @ToolParam(required = false, description = "Focused keywords such as grounded class, method, entity, repository method, endpoint, queue, event type, exception or downstream client.")
            List<String> keywords,
            @ToolParam(required = false, description = "Operation names inferred from logs/traces.")
            List<String> operationNames,
            @ToolParam(required = false, description = "Maximum files per inferred role. Defaults to 5.")
            Integer maxFilesPerRole,
            @ToolParam(required = false, description = "Krotki powod po polsku: w jakim celu model szuka kontekstu przeplywu.")
            String reason,
            ToolContext toolContext
    ) {
        var scope = scope(firstProjectName(projectNames), applicationName, branchRef, toolContext);
        var safeProjectNames = defaultList(projectNames);
        var safeOperationNames = defaultList(operationNames);
        var searchKeywords = deduplicate(defaultList(keywords));
        var effectiveMaxFilesPerRole = normalizeMaxFilesPerRole(maxFilesPerRole);

        log.info(
                "Tool request [{}] runReference={} group={} branch={} applicationName={} analysisRunId={} copilotSessionId={} toolCallId={} projectNames={} operationNames={} searchKeywords={} maxFilesPerRole={}",
                FIND_FLOW_CONTEXT,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                abbreviateList(safeProjectNames),
                abbreviateList(safeOperationNames),
                abbreviateList(searchKeywords),
                effectiveMaxFilesPerRole
        );

        var candidates = gitLabRepositoryPort.searchCandidateFiles(new GitLabRepositorySearchQuery(
                null,
                scope.group(),
                scope.branch(),
                safeProjectNames,
                safeOperationNames,
                searchKeywords
        ));
        var flowCandidates = toFlowContextCandidates(candidates);
        var groups = groupFlowCandidates(flowCandidates, effectiveMaxFilesPerRole);
        var recommendedNextReads = recommendedNextReads(flowCandidates);

        log.info(
                "Tool result [{}] runReference={} group={} branch={} applicationName={} candidateCount={} groupCount={} recommendedNextReadsCount={}",
                FIND_FLOW_CONTEXT,
                scope.runReference(),
                scope.group(),
                scope.branch(),
                scope.applicationName(),
                flowCandidates.size(),
                groups.size(),
                recommendedNextReads.size()
        );

        return new GitLabFindFlowContextToolResponse(
                scope.group(),
                scope.branch(),
                groups,
                recommendedNextReads
        );
    }

    List<GitLabFlowContextCandidate> toFlowContextCandidates(List<pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate> candidates) {
        return defaultList(candidates).stream()
                .map(candidate -> {
                    var inferredRole = inferRole(candidate.filePath(), candidate.matchReason());
                    var recommendedReadStrategy = recommendReadStrategy(candidate.filePath(), inferredRole);
                    return new GitLabFlowContextCandidate(
                            candidate.group(),
                            candidate.projectName(),
                            candidate.branch(),
                            candidate.filePath(),
                            candidate.matchReason(),
                            candidate.matchScore(),
                            inferredRole,
                            recommendedReadStrategy,
                            abbreviate(candidate.matchReason(), PREVIEW_MAX_CHARACTERS)
                    );
                })
                .toList();
    }

    List<GitLabFlowContextGroup> groupFlowCandidates(
            List<GitLabFlowContextCandidate> flowCandidates,
            int maxFilesPerRole
    ) {
        var groupedCandidates = new LinkedHashMap<String, List<GitLabFlowContextCandidate>>();
        for (var candidate : defaultList(flowCandidates)) {
            groupedCandidates.computeIfAbsent(candidate.inferredRole(), ignored -> new ArrayList<>())
                    .add(candidate);
        }

        return groupedCandidates.entrySet().stream()
                .map(entry -> new GitLabFlowContextGroup(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparingInt(GitLabFlowContextCandidate::matchScore).reversed()
                                        .thenComparing(GitLabFlowContextCandidate::projectName)
                                        .thenComparing(GitLabFlowContextCandidate::filePath))
                                .limit(maxFilesPerRole)
                                .toList()
                ))
                .sorted(Comparator.comparingInt((GitLabFlowContextGroup candidate) -> rolePriority(candidate.role()))
                        .thenComparing(GitLabFlowContextGroup::role))
                .toList();
    }

    List<String> recommendedNextReads(List<GitLabFlowContextCandidate> flowCandidates) {
        return defaultList(flowCandidates).stream()
                .sorted(Comparator.comparingInt(GitLabFlowContextCandidate::matchScore).reversed()
                        .thenComparing(GitLabFlowContextCandidate::projectName)
                        .thenComparing(GitLabFlowContextCandidate::filePath))
                .limit(MAX_RECOMMENDED_NEXT_READS)
                .map(candidate -> "%s:%s (%s, %s)".formatted(
                        candidate.projectName(),
                        candidate.filePath(),
                        candidate.inferredRole(),
                        candidate.recommendedReadStrategy()
                ))
                .toList();
    }

    String inferRole(String filePath, String contentOrReason) {
        var value = (safeValue(filePath) + "\n" + safeValue(contentOrReason)).toLowerCase(Locale.ROOT);

        if (containsAny(value, "controller", "resource", "endpoint")) {
            return "entrypoint";
        }
        if (containsAny(value, "listener", "consumer", "handler")) {
            return "listener-or-handler";
        }
        if (containsAny(value, "scheduler", "scheduled", "job")) {
            return "scheduler-or-job";
        }
        if (containsAny(value, "outbox", "eventpublisher", "eventhandler", "message")) {
            return "outbox-or-event";
        }
        if (containsAny(value, "repository", "jparepository", "crudrepository", "dao")) {
            return "repository";
        }
        if (containsAny(value, "mapper", "converter", "assembler")) {
            return "mapper";
        }
        if (containsAny(value, "validator", "validation")) {
            return "validator";
        }
        if (containsAny(value, "client", "gateway", "http", "resttemplate", "webclient", "feign")) {
            return "downstream-client";
        }
        if (containsAny(value, "service", "facade", "orchestrator", "processor", "manager")) {
            return "service-or-orchestrator";
        }
        if (containsAny(value, "entity", "embeddable", "mappedsuperclass")) {
            return "entity";
        }
        if (containsAny(value, "configuration", "properties", "config")) {
            return "configuration";
        }

        return "other";
    }

    String recommendReadStrategy(String filePath, String role) {
        var normalizedPath = safeValue(filePath).toLowerCase(Locale.ROOT);

        if (normalizedPath.endsWith(".yml")
                || normalizedPath.endsWith(".yaml")
                || normalizedPath.endsWith(".properties")) {
            return "read-file-if-short";
        }
        if ("entrypoint".equals(role)
                || "service-or-orchestrator".equals(role)
                || "listener-or-handler".equals(role)
                || "outbox-or-event".equals(role)
                || "downstream-client".equals(role)) {
            return "outline-then-focused-chunk";
        }
        if ("repository".equals(role) || "entity".equals(role)) {
            return "outline-or-focused-chunk";
        }

        return "focused-chunk";
    }

    int rolePriority(String role) {
        return switch (role) {
            case "entrypoint" -> 1;
            case "service-or-orchestrator" -> 2;
            case "listener-or-handler" -> 3;
            case "outbox-or-event" -> 4;
            case "repository" -> 5;
            case "validator" -> 6;
            case "mapper" -> 7;
            case "downstream-client" -> 8;
            case "scheduler-or-job" -> 9;
            case "entity" -> 10;
            case "configuration" -> 11;
            default -> 12;
        };
    }

    FileOutline buildOutline(String content) {
        if (!StringUtils.hasText(content)) {
            return new FileOutline(null, List.of(), List.of(), List.of(), List.of(), List.of());
        }

        var packageName = extractPackageName(content);
        var imports = limitList(deduplicate(extractMatches(content, IMPORT_PATTERN)), MAX_IMPORTS);
        var javaOutline = extractJavaOutline(content);

        return new FileOutline(
                packageName,
                imports,
                javaOutline.typeSummaries(),
                javaOutline.fieldSummaries(),
                javaOutline.constructorSummaries(),
                javaOutline.methodSummaries()
        );
    }

    private JavaOutline extractJavaOutline(String content) {
        try {
            var javaParser = new JavaParser(new ParserConfiguration()
                    .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
            var result = javaParser.parse(ParseStart.COMPILATION_UNIT, Providers.provider(content));
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                return JavaOutline.empty();
            }
            var compilationUnit = result.getResult().get();
            var sourceLines = content.lines().toList();
            var typeSummaries = limitList(compilationUnit.findAll(TypeDeclaration.class).stream()
                    .map(type -> typeSummary(type, sourceLines))
                    .sorted(Comparator.comparingInt(GitLabJavaTypeSummary::lineStart))
                    .toList(), MAX_TYPE_SUMMARIES);
            var fieldSummaries = limitList(compilationUnit.findAll(FieldDeclaration.class).stream()
                    .flatMap(field -> field.getVariables().stream()
                            .map(variable -> fieldSummary(field, variable)))
                    .sorted(Comparator.comparingInt(GitLabJavaFieldSummary::lineStart))
                    .toList(), MAX_FIELD_SUMMARIES);
            var constructorSummaries = limitList(compilationUnit.findAll(ConstructorDeclaration.class).stream()
                    .map(this::constructorSummary)
                    .sorted(Comparator.comparingInt(GitLabJavaConstructorSummary::lineStart))
                    .toList(), MAX_CONSTRUCTOR_SUMMARIES);
            var methodSummaries = limitList(compilationUnit.findAll(MethodDeclaration.class).stream()
                    .map(this::methodSummary)
                    .sorted(Comparator.comparingInt(GitLabJavaMethodSummary::lineStart))
                    .toList(), MAX_METHOD_SUMMARIES);
            return new JavaOutline(typeSummaries, fieldSummaries, constructorSummaries, methodSummaries);
        } catch (RuntimeException exception) {
            return JavaOutline.empty();
        }
    }

    private GitLabJavaTypeSummary typeSummary(TypeDeclaration<?> type, List<String> sourceLines) {
        return new GitLabJavaTypeSummary(
                type.getNameAsString(),
                type.getFullyQualifiedName().orElse(type.getNameAsString()),
                typeKind(type),
                typeDeclarationLine(type, sourceLines),
                lineStart(type),
                lineEnd(type),
                modifierKeywords(type.getModifiers()),
                annotationTexts(type.getAnnotations())
        );
    }

    private GitLabJavaFieldSummary fieldSummary(
            FieldDeclaration field,
            VariableDeclarator variable
    ) {
        return new GitLabJavaFieldSummary(
                declaringTypeName(field),
                variable.getNameAsString(),
                variable.getType().asString(),
                lineStart(field),
                lineEnd(field),
                modifierKeywords(field.getModifiers()),
                annotationTexts(field.getAnnotations())
        );
    }

    private GitLabJavaConstructorSummary constructorSummary(ConstructorDeclaration constructor) {
        return new GitLabJavaConstructorSummary(
                declaringTypeName(constructor),
                normalizeWhitespace(constructor.getDeclarationAsString(true, false, true)),
                lineStart(constructor),
                lineEnd(constructor),
                modifierKeywords(constructor.getModifiers()),
                annotationTexts(constructor.getAnnotations())
        );
    }

    private GitLabJavaMethodSummary methodSummary(MethodDeclaration method) {
        return new GitLabJavaMethodSummary(
                declaringTypeName(method),
                normalizeWhitespace(method.getDeclarationAsString(true, false, true)),
                lineStart(method),
                lineEnd(method),
                modifierKeywords(method.getModifiers()),
                annotationTexts(method.getAnnotations())
        );
    }

    private String declaringTypeName(Node node) {
        var declaringType = node.findAncestor(TypeDeclaration.class).orElse(null);
        if (declaringType == null) {
            return null;
        }
        var fullyQualifiedName = declaringType.getFullyQualifiedName();
        if (fullyQualifiedName.isPresent()) {
            return String.valueOf(fullyQualifiedName.get());
        }
        return declaringType.getNameAsString();
    }

    private String typeKind(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            return classOrInterfaceDeclaration.isInterface() ? "interface" : "class";
        }
        if (type instanceof EnumDeclaration) {
            return "enum";
        }
        if (type instanceof RecordDeclaration) {
            return "record";
        }
        if (type instanceof AnnotationDeclaration) {
            return "annotation";
        }
        return "type";
    }

    private String declarationLine(Node node, List<String> sourceLines) {
        var lineStart = lineStart(node);
        if (lineStart > 0 && lineStart <= sourceLines.size()) {
            return sourceLines.get(lineStart - 1).trim();
        }
        return null;
    }

    private String typeDeclarationLine(TypeDeclaration<?> type, List<String> sourceLines) {
        var startLine = lineStart(type);
        var endLine = lineEnd(type);
        var upperBound = endLine > 0 ? Math.min(endLine, sourceLines.size()) : sourceLines.size();
        for (var lineNumber = Math.max(startLine, 1); lineNumber <= upperBound; lineNumber++) {
            var line = sourceLines.get(lineNumber - 1).trim();
            if (looksLikeTypeDeclaration(line, type)) {
                return line;
            }
        }
        return declarationLine(type, sourceLines);
    }

    private boolean looksLikeTypeDeclaration(String line, TypeDeclaration<?> type) {
        if (!StringUtils.hasText(line) || line.startsWith("@") || !line.contains(type.getNameAsString())) {
            return false;
        }
        return switch (typeKind(type)) {
            case "class" -> line.contains("class ");
            case "interface" -> line.contains("interface ");
            case "enum" -> line.contains("enum ");
            case "record" -> line.contains("record ");
            case "annotation" -> line.contains("@interface ");
            default -> true;
        };
    }

    private List<String> modifierKeywords(NodeList<Modifier> modifiers) {
        return modifiers.stream()
                .map(modifier -> modifier.getKeyword().asString())
                .toList();
    }

    private List<String> annotationTexts(NodeList<AnnotationExpr> annotations) {
        return annotations.stream()
                .map(annotation -> normalizeWhitespace(annotation.toString()))
                .filter(StringUtils::hasText)
                .toList();
    }

    private String normalizeWhitespace(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("\\s+", " ").trim() : value;
    }

    private int lineStart(Node node) {
        return node.getRange()
                .map(range -> range.begin.line)
                .orElse(0);
    }

    private int lineEnd(Node node) {
        return node.getRange()
                .map(range -> range.end.line)
                .orElse(0);
    }

    String simpleName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        var trimmed = value.trim();
        var separatorIndex = trimmed.lastIndexOf('.');
        return separatorIndex >= 0 ? trimmed.substring(separatorIndex + 1) : trimmed;
    }

    String fileNameWithoutExtension(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return null;
        }

        var normalizedPath = filePath.replace('\\', '/');
        var fileName = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
        var extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
    }

    List<String> deduplicate(List<String> values) {
        var deduplicated = new LinkedHashSet<String>();

        for (var value : defaultList(values)) {
            if (!StringUtils.hasText(value)) {
                continue;
            }

            deduplicated.add(value.trim());
        }

        return List.copyOf(deduplicated);
    }

    List<String> classReferenceKeywords(String className) {
        if (!StringUtils.hasText(className)) {
            return List.of();
        }

        var normalizedClassName = className.trim();
        var searchKeywords = new ArrayList<String>();
        searchKeywords.add(normalizedClassName);

        var simpleName = simpleName(normalizedClassName);
        if (StringUtils.hasText(simpleName) && !normalizedClassName.equals(simpleName)) {
            searchKeywords.add(simpleName);
            searchKeywords.add("import " + normalizedClassName + ";");
        }

        return deduplicate(searchKeywords);
    }

    private <T> List<T> defaultList(List<T> values) {
        return values != null ? values : List.of();
    }

    private List<String> normalizeRepositoryFilePaths(List<String> filePaths, String projectName) {
        var normalizedPaths = new LinkedHashSet<String>();

        for (var filePath : defaultList(filePaths)) {
            var normalizedPath = normalizeRepositoryFilePath(filePath, projectName);
            if (StringUtils.hasText(normalizedPath)) {
                normalizedPaths.add(normalizedPath);
            }
        }

        return List.copyOf(normalizedPaths);
    }

    private String normalizeRepositoryFilePath(String filePath, String projectName) {
        if (!StringUtils.hasText(filePath)) {
            return null;
        }

        var normalizedPath = filePath.trim().replace('\\', '/');
        var nextReadHintIndex = normalizedPath.indexOf(" via gitlab_");
        if (nextReadHintIndex > 0) {
            normalizedPath = normalizedPath.substring(0, nextReadHintIndex).trim();
        }
        var lineHintIndex = normalizedPath.indexOf(" lines ");
        if (lineHintIndex > 0) {
            normalizedPath = normalizedPath.substring(0, lineHintIndex).trim();
        }
        var projectSeparatorIndex = normalizedPath.indexOf(':');
        if (projectSeparatorIndex > 0 && projectSeparatorIndex + 1 < normalizedPath.length()) {
            var candidateProjectName = normalizedPath.substring(0, projectSeparatorIndex).trim();
            var candidateFilePath = normalizedPath.substring(projectSeparatorIndex + 1).trim();
            if (StringUtils.hasText(candidateFilePath)
                    && (candidateProjectName.equals(projectName) || candidateFilePath.startsWith("src/"))) {
                normalizedPath = candidateFilePath;
            }
        }
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        return normalizedPath;
    }

    private <T> List<T> limitList(List<T> values, int maxItems) {
        if (values.size() <= maxItems) {
            return List.copyOf(values);
        }

        return List.copyOf(values.subList(0, maxItems));
    }

    private String abbreviateList(List<String> values) {
        var safeValues = defaultList(values);

        if (safeValues.isEmpty()) {
            return "[]";
        }

        var maxItems = Math.min(safeValues.size(), 3);
        var preview = safeValues.subList(0, maxItems);
        return safeValues.size() > maxItems
                ? preview + " ... (" + safeValues.size() + " items)"
                : preview.toString();
    }

    private boolean containsAny(String value, String... needles) {
        if (!StringUtils.hasText(value)) {
            return false;
        }

        var normalizedValue = value.toLowerCase(Locale.ROOT);
        for (var needle : needles) {
            if (needle != null && normalizedValue.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }

    private List<String> joinLists(List<String> left, List<String> right) {
        var joined = new ArrayList<String>();
        joined.addAll(defaultList(left));
        joined.addAll(defaultList(right));
        return joined;
    }

    private int normalizeMaxFilesPerRole(Integer maxFilesPerRole) {
        var value = normalizePositiveLimit(maxFilesPerRole, DEFAULT_MAX_FILES_PER_ROLE);
        return Math.min(MAX_FILES_PER_ROLE, Math.max(1, value));
    }

    private int normalizePositiveLimit(Integer limit, int defaultValue) {
        return limit != null && limit > 0 ? limit : defaultValue;
    }

    private int safeLength(String value) {
        return value != null ? value.length() : 0;
    }

    private String responseGroup(GitLabRepositoryFileContent fileContent, String fallbackGroup) {
        return fileContent != null && StringUtils.hasText(fileContent.group())
                ? fileContent.group()
                : fallbackGroup;
    }

    private String responseProjectName(GitLabRepositoryFileContent fileContent, String fallbackProjectName) {
        return fileContent != null && StringUtils.hasText(fileContent.projectName())
                ? fileContent.projectName()
                : fallbackProjectName;
    }

    private String responseBranch(GitLabRepositoryFileContent fileContent, String fallbackBranch) {
        return fileContent != null && StringUtils.hasText(fileContent.branch())
                ? fileContent.branch()
                : fallbackBranch;
    }

    private String responseFilePath(GitLabRepositoryFileContent fileContent, String fallbackFilePath) {
        return fileContent != null && StringUtils.hasText(fileContent.filePath())
                ? fileContent.filePath()
                : fallbackFilePath;
    }

    private FileMetadataSnapshot readFileMetadata(GitLabToolScope scope, String projectName, String filePath) {
        try {
            var metadata = gitLabRepositoryPort.readFileMetadata(
                    scope.group(),
                    projectName,
                    scope.branch(),
                    filePath
            );
            return FileMetadataSnapshot.from(metadata);
        } catch (RuntimeException exception) {
            var error = toolErrorMessage(exception);
            log.warn(
                    "Tool partial metadata failure [{}] runReference={} group={} branch={} applicationName={} projectName={} filePath={} reason={}",
                    READ_REPOSITORY_FILES_BY_PATH,
                    scope.runReference(),
                    scope.group(),
                    scope.branch(),
                    scope.applicationName(),
                    projectName,
                    filePath,
                    error
            );
            return FileMetadataSnapshot.failed(error);
        }
    }

    private String extractPackageName(String content) {
        var matcher = PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private List<String> extractMatches(String content, Pattern pattern) {
        var matches = new ArrayList<String>();
        var matcher = pattern.matcher(content);

        while (matcher.find()) {
            matches.add(matcher.group().trim());
        }

        return matches;
    }

    private String abbreviate(String value, int maxCharacters) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        var normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > maxCharacters
                ? normalized.substring(0, maxCharacters) + "..."
                : normalized;
    }

    private String toolErrorMessage(RuntimeException exception) {
        if (exception == null) {
            return null;
        }

        var message = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
        return exception.getClass().getSimpleName() + ": " + abbreviate(message, PREVIEW_MAX_CHARACTERS);
    }

    private String safeValue(String value) {
        return value != null ? value : "";
    }

    private static OperationalContextCatalog emptyOperationalContextCatalog() {
        return OperationalContextCatalog.empty();
    }

    private record FileMetadataSnapshot(
            Long sizeBytes,
            String contentSha256,
            String blobId,
            String commitId,
            String lastCommitId,
            String lastModifiedAt,
            String status,
            String error
    ) {
        private static FileMetadataSnapshot from(GitLabRepositoryFileMetadata metadata) {
            if (metadata == null) {
                return unavailable();
            }

            return new FileMetadataSnapshot(
                    metadata.sizeBytes(),
                    metadata.contentSha256(),
                    metadata.blobId(),
                    metadata.commitId(),
                    metadata.lastCommitId(),
                    metadata.lastModifiedAt(),
                    "RESOLVED",
                    null
            );
        }

        private static FileMetadataSnapshot unavailable() {
            return new FileMetadataSnapshot(null, null, null, null, null, null, "UNAVAILABLE", null);
        }

        private static FileMetadataSnapshot failed(String error) {
            return new FileMetadataSnapshot(null, null, null, null, null, null, "FAILED", error);
        }
    }

    record FileOutline(
            String packageName,
            List<String> imports,
            List<GitLabJavaTypeSummary> typeSummaries,
            List<GitLabJavaFieldSummary> fieldSummaries,
            List<GitLabJavaConstructorSummary> constructorSummaries,
            List<GitLabJavaMethodSummary> methodSummaries
    ) {
    }

    private record JavaOutline(
            List<GitLabJavaTypeSummary> typeSummaries,
            List<GitLabJavaFieldSummary> fieldSummaries,
            List<GitLabJavaConstructorSummary> constructorSummaries,
            List<GitLabJavaMethodSummary> methodSummaries
    ) {
        private static JavaOutline empty() {
            return new JavaOutline(List.of(), List.of(), List.of(), List.of());
        }
    }

}
