package pl.mkn.incidenttracker.analysis.mcp.gitlab;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositorySearchQuery;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabFileChunkRequest;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabFileChunkResult;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabFindClassReferencesToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabFindFlowContextToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabFlowContextCandidate;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabFlowContextGroup;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabReadRepositoryFileChunksToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabReadRepositoryFileChunkToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabReadRepositoryFileOutlineToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabReadRepositoryFileToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabSearchRepositoryCandidatesToolResponse;
import pl.mkn.incidenttracker.analysis.mcp.gitlab.GitLabToolDtos.GitLabToolScope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class GitLabMcpTools {

    private static final int DEFAULT_MAX_CHARACTERS = 4_000;
    private static final int DEFAULT_OUTLINE_MAX_CHARACTERS = 30_000;
    private static final int DEFAULT_MAX_TOTAL_CHUNK_CHARACTERS = 20_000;
    private static final int DEFAULT_MAX_FILES_PER_ROLE = 5;
    private static final int MAX_FILES_PER_ROLE = 10;
    private static final int MAX_BATCH_CHUNKS = 8;
    private static final int MAX_IMPORTS = 40;
    private static final int MAX_CLASSES = 20;
    private static final int MAX_ANNOTATIONS = 40;
    private static final int MAX_METHOD_SIGNATURES = 80;
    private static final int MAX_RECOMMENDED_NEXT_READS = 8;
    private static final int PREVIEW_MAX_CHARACTERS = 200;

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?[\\w.*]+\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:(?:public|protected|private|abstract|final|sealed|non-sealed|static)\\s+)*(?:class|interface|enum|record)\\s+[A-Za-z_$][\\w$]*\\b.*$"
    );
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile(
            "(?m)^\\s*@[A-Za-z_$][\\w$.]*(?:\\([^\\r\\n]*\\))?\\s*$"
    );
    private static final Pattern METHOD_SIGNATURE_START_PATTERN = Pattern.compile(
            "^(?:(?:public|protected|private|static|final|abstract|synchronized|default|native|strictfp|sealed|non-sealed)\\s+)*(?:<[^>]+>\\s+)?(?:[\\w$@.<>\\[\\],?]+\\s+)?[A-Za-z_$][\\w$]*\\s*\\("
    );

    private final GitLabRepositoryPort gitLabRepositoryPort;

    @Tool(
            name = "gitlab_search_repository_candidates",
            description = """
                    Search GitLab repository files in the current fixed session group and branch using candidate project names,
                    operation names, class names, method names, entity names, exception names and keywords inferred from logs,
                    traces or prior analysis. Use this when the affected project/file is unclear or when broader repository
                    candidates are needed before focused reads.
                    """
    )
    public GitLabSearchRepositoryCandidatesToolResponse searchRepositoryCandidates(
            @ToolParam(required = false, description = "One or more candidate GitLab project paths inside the fixed session group. Relative subgroup segments are allowed.")
            List<String> projectNames,
            @ToolParam(required = false, description = "Operation names inferred from traces.")
            List<String> operationNames,
            @ToolParam(required = false, description = "Keywords inferred from logs, traces, code identifiers or the current hypothesis.")
            List<String> keywords,
            ToolContext toolContext
    ) {
        var scope = GitLabToolScope.from(toolContext);
        var safeProjectNames = defaultList(projectNames);
        var safeOperationNames = defaultList(operationNames);
        var safeKeywords = defaultList(keywords);

        log.info(
                "Tool request [{}] correlationId={} group={} branch={} environment={} analysisRunId={} copilotSessionId={} toolCallId={} projectNames={} operationNames={} keywords={}",
                "gitlab_search_repository_candidates",
                scope.correlationId(),
                scope.group(),
                scope.branch(),
                scope.environment(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                abbreviateList(safeProjectNames),
                abbreviateList(safeOperationNames),
                abbreviateList(safeKeywords)
        );

        var query = new GitLabRepositorySearchQuery(
                scope.correlationId(),
                scope.group(),
                scope.branch(),
                safeProjectNames,
                safeOperationNames,
                safeKeywords
        );
        var candidates = gitLabRepositoryPort.searchCandidateFiles(query);

        log.info(
                "Tool result [{}] correlationId={} group={} branch={} environment={} candidateCount={} candidatePaths={}",
                "gitlab_search_repository_candidates",
                scope.correlationId(),
                scope.group(),
                scope.branch(),
                scope.environment(),
                candidates.size(),
                abbreviateList(candidates.stream()
                        .map(candidate -> candidate.projectName() + ":" + candidate.filePath())
                        .toList())
        );

        return new GitLabSearchRepositoryCandidatesToolResponse(candidates);
    }

    @Tool(
            name = "gitlab_read_repository_file",
            description = """
                    Read a file from the GitLab repository in the current fixed session group and branch.
                    Use only when full class/file context is necessary. Prefer outline/chunk/chunks before full file reads for large files.
                    """
    )
    public GitLabReadRepositoryFileToolResponse readRepositoryFile(
            @ToolParam(description = "GitLab project path inside the fixed session group.")
            String projectName,
            @ToolParam(description = "Repository file path.")
            String filePath,
            @ToolParam(required = false, description = "Maximum number of characters to return. Defaults to 4000.")
            Integer maxCharacters,
            ToolContext toolContext
    ) {
        var scope = GitLabToolScope.from(toolContext);
        var effectiveMaxCharacters = normalizePositiveLimit(maxCharacters, DEFAULT_MAX_CHARACTERS);

        log.info(
                "Tool request [{}] correlationId={} group={} branch={} environment={} analysisRunId={} copilotSessionId={} toolCallId={} projectName={} filePath={} maxCharacters={}",
                "gitlab_read_repository_file",
                scope.correlationId(),
                scope.group(),
                scope.branch(),
                scope.environment(),
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
                "Tool result [{}] correlationId={} group={} branch={} environment={} projectName={} filePath={} returnedCharacters={} truncated={}",
                "gitlab_read_repository_file",
                scope.correlationId(),
                responseGroup(fileContent, scope.group()),
                responseBranch(fileContent, scope.branch()),
                scope.environment(),
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
            name = "gitlab_read_repository_file_chunk",
            description = """
                    Read only a selected line range from a GitLab repository file in the current fixed session group and branch.
                    Line numbers are 1-based and inclusive. Prefer this over full file reads when investigating a stack frame,
                    method, repository predicate, mapper, validator, or client call.
                    """
    )
    public GitLabReadRepositoryFileChunkToolResponse readRepositoryFileChunk(
            @ToolParam(description = "GitLab project path inside the fixed session group.")
            String projectName,
            @ToolParam(description = "Repository file path.")
            String filePath,
            @ToolParam(description = "First line to return. Uses 1-based inclusive numbering.")
            int startLine,
            @ToolParam(description = "Last line to return. Uses 1-based inclusive numbering.")
            int endLine,
            @ToolParam(required = false, description = "Maximum number of characters to return. Defaults to 4000.")
            Integer maxCharacters,
            ToolContext toolContext
    ) {
        var scope = GitLabToolScope.from(toolContext);
        var effectiveMaxCharacters = normalizePositiveLimit(maxCharacters, DEFAULT_MAX_CHARACTERS);

        log.info(
                "Tool request [{}] correlationId={} group={} branch={} environment={} analysisRunId={} copilotSessionId={} toolCallId={} projectName={} filePath={} requestedStartLine={} requestedEndLine={} maxCharacters={}",
                "gitlab_read_repository_file_chunk",
                scope.correlationId(),
                scope.group(),
                scope.branch(),
                scope.environment(),
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
                "Tool result [{}] correlationId={} group={} branch={} environment={} projectName={} filePath={} returnedStartLine={} returnedEndLine={} totalLines={} returnedCharacters={} truncated={}",
                "gitlab_read_repository_file_chunk",
                scope.correlationId(),
                fileChunk != null ? fileChunk.group() : scope.group(),
                fileChunk != null ? fileChunk.branch() : scope.branch(),
                scope.environment(),
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
            name = "gitlab_read_repository_file_outline",
            description = """
                    Read a lightweight outline of a GitLab repository file in the current fixed session group and branch:
                    package, imports summary, class/interface names, annotations, method signatures and inferred file role.
                    Use before reading a full file when the model needs to understand the class role in the broader flow without loading all code.
                    """
    )
    public GitLabReadRepositoryFileOutlineToolResponse readRepositoryFileOutline(
            @ToolParam(description = "GitLab project path inside the fixed session group.")
            String projectName,
            @ToolParam(description = "Repository file path.")
            String filePath,
            @ToolParam(required = false, description = "Maximum number of characters to read before extracting outline. Defaults to 30000.")
            Integer maxCharacters,
            ToolContext toolContext
    ) {
        var scope = GitLabToolScope.from(toolContext);
        var effectiveMaxCharacters = normalizePositiveLimit(maxCharacters, DEFAULT_OUTLINE_MAX_CHARACTERS);

        log.info(
                "Tool request [{}] correlationId={} group={} branch={} environment={} analysisRunId={} copilotSessionId={} toolCallId={} projectName={} filePath={} maxCharacters={}",
                "gitlab_read_repository_file_outline",
                scope.correlationId(),
                scope.group(),
                scope.branch(),
                scope.environment(),
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
                "Tool result [{}] correlationId={} group={} branch={} environment={} projectName={} filePath={} packageName={} importCount={} classCount={} annotationCount={} methodSignatureCount={} inferredRole={} truncated={}",
                "gitlab_read_repository_file_outline",
                scope.correlationId(),
                responseGroup(fileContent, scope.group()),
                responseBranch(fileContent, scope.branch()),
                scope.environment(),
                responseProjectName(fileContent, projectName),
                responseFilePath(fileContent, filePath),
                outline.packageName(),
                outline.imports().size(),
                outline.classes().size(),
                outline.annotations().size(),
                outline.methodSignatures().size(),
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
                outline.classes(),
                outline.annotations(),
                outline.methodSignatures(),
                inferredRole,
                fileContent != null && fileContent.truncated()
        );
    }

    @Tool(
            name = "gitlab_read_repository_file_chunks",
            description = """
                    Read several focused line ranges from GitLab files in the current fixed session group and branch in one tool call.
                    Use when multiple directly related chunks are needed to explain the affected flow, for example service + repository + mapper
                    or listener + outbox handler + downstream client. The server enforces maximum chunks and total characters.
                    """
    )
    public GitLabReadRepositoryFileChunksToolResponse readRepositoryFileChunks(
            @ToolParam(description = "Focused chunk requests. Maximum 8 chunks are processed.")
            List<GitLabFileChunkRequest> chunks,
            @ToolParam(required = false, description = "Maximum total characters returned across all chunks. Defaults to 20000.")
            Integer maxTotalCharacters,
            ToolContext toolContext
    ) {
        var scope = GitLabToolScope.from(toolContext);
        var safeChunks = defaultList(chunks);
        var processedChunks = safeChunks.stream()
                .filter(chunk -> chunk != null)
                .limit(MAX_BATCH_CHUNKS)
                .toList();
        var effectiveMaxTotalCharacters = normalizePositiveLimit(
                maxTotalCharacters,
                DEFAULT_MAX_TOTAL_CHUNK_CHARACTERS
        );

        log.info(
                "Tool request [{}] correlationId={} group={} branch={} environment={} analysisRunId={} copilotSessionId={} toolCallId={} requestedChunkCount={} processedChunkCount={} chunkTargets={} maxTotalCharacters={}",
                "gitlab_read_repository_file_chunks",
                scope.correlationId(),
                scope.group(),
                scope.branch(),
                scope.environment(),
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
                "Tool result [{}] correlationId={} group={} branch={} environment={} chunkCount={} chunkCountTruncated={} totalCharacterLimitReached={}",
                "gitlab_read_repository_file_chunks",
                scope.correlationId(),
                scope.group(),
                scope.branch(),
                scope.environment(),
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
            name = "gitlab_find_class_references",
            description = """
                    Finds files in the current fixed session group and branch that declare, import or directly use a grounded class.
                    Use this when an exception, stacktrace or deterministic code evidence points to an entity, repository, DTO,
                    mapper, validator or client class and you need surrounding code to infer repository predicates, JPA/table hints,
                    direct relations or the broader flow before DB diagnostics.
                    """
    )
    public GitLabFindClassReferencesToolResponse findClassReferences(
            @ToolParam(required = false, description = "Candidate GitLab project paths inside the fixed session group.")
            List<String> projectNames,
            @ToolParam(description = "Grounded fully qualified or simple class name, for example pl.mkn.orders.domain.OrderEntity.")
            String className,
            @ToolParam(required = false, description = "Optional relation or mapping hints such as @Entity, @Table, repository method, JoinColumn, mappedBy, business key or exception name.")
            List<String> relatedHints,
            @ToolParam(required = false, description = "Operation names inferred from logs/traces.")
            List<String> operationNames,
            @ToolParam(required = false, description = "Maximum files per inferred role. Defaults to 5.")
            Integer maxFilesPerRole,
            ToolContext toolContext
    ) {
        var scope = GitLabToolScope.from(toolContext);
        var safeProjectNames = defaultList(projectNames);
        var safeOperationNames = defaultList(operationNames);
        var searchKeywords = deduplicate(joinLists(
                seedClassReferenceKeywords(className),
                defaultList(relatedHints)
        ));
        var effectiveMaxFilesPerRole = normalizeMaxFilesPerRole(maxFilesPerRole);

        log.info(
                "Tool request [{}] correlationId={} group={} branch={} environment={} analysisRunId={} copilotSessionId={} toolCallId={} className={} projectNames={} operationNames={} searchKeywords={} maxFilesPerRole={}",
                "gitlab_find_class_references",
                scope.correlationId(),
                scope.group(),
                scope.branch(),
                scope.environment(),
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
                scope.correlationId(),
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
                "Tool result [{}] correlationId={} group={} branch={} environment={} candidateCount={} groupCount={} recommendedNextReadsCount={}",
                "gitlab_find_class_references",
                scope.correlationId(),
                scope.group(),
                scope.branch(),
                scope.environment(),
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
            name = "gitlab_find_flow_context",
            description = """
                    Finds a small set of directly related files in the current fixed session group and branch that explain the functional
                    or technical flow around a failing class, method, entity, repository method, endpoint, queue, message type or integration keyword.
                    Returns grouped candidates by likely role: entrypoint, service/orchestrator, repository, mapper, validator,
                    downstream client, listener, scheduler, outbox/event handler, entity or configuration.
                    Use when the local error is known but the broader affected function is not yet clear.
                    """
    )
    public GitLabFindFlowContextToolResponse findFlowContext(
            @ToolParam(required = false, description = "Candidate GitLab project paths inside the fixed session group.")
            List<String> projectNames,
            @ToolParam(required = false, description = "Seed class from stacktrace, logs or deterministic evidence.")
            String seedClass,
            @ToolParam(required = false, description = "Seed method from stacktrace, logs or deterministic evidence.")
            String seedMethod,
            @ToolParam(required = false, description = "Seed file path from deterministic evidence.")
            String seedFilePath,
            @ToolParam(required = false, description = "Keywords such as entity, repository method, endpoint, queue, event type, exception or downstream client.")
            List<String> keywords,
            @ToolParam(required = false, description = "Operation names inferred from logs/traces.")
            List<String> operationNames,
            @ToolParam(required = false, description = "Maximum files per inferred role. Defaults to 5.")
            Integer maxFilesPerRole,
            ToolContext toolContext
    ) {
        var scope = GitLabToolScope.from(toolContext);
        var safeProjectNames = defaultList(projectNames);
        var safeOperationNames = defaultList(operationNames);
        var searchKeywords = deduplicate(joinLists(
                seedClassReferenceKeywords(seedClass),
                seedKeywords(
                seedMethod,
                fileNameWithoutExtension(seedFilePath)
                )
        ));
        searchKeywords = deduplicate(joinLists(searchKeywords, defaultList(keywords)));
        var effectiveMaxFilesPerRole = normalizeMaxFilesPerRole(maxFilesPerRole);

        log.info(
                "Tool request [{}] correlationId={} group={} branch={} environment={} analysisRunId={} copilotSessionId={} toolCallId={} projectNames={} operationNames={} searchKeywords={} maxFilesPerRole={}",
                "gitlab_find_flow_context",
                scope.correlationId(),
                scope.group(),
                scope.branch(),
                scope.environment(),
                scope.analysisRunId(),
                scope.copilotSessionId(),
                scope.toolCallId(),
                abbreviateList(safeProjectNames),
                abbreviateList(safeOperationNames),
                abbreviateList(searchKeywords),
                effectiveMaxFilesPerRole
        );

        var candidates = gitLabRepositoryPort.searchCandidateFiles(new GitLabRepositorySearchQuery(
                scope.correlationId(),
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
                "Tool result [{}] correlationId={} group={} branch={} environment={} candidateCount={} groupCount={} recommendedNextReadsCount={}",
                "gitlab_find_flow_context",
                scope.correlationId(),
                scope.group(),
                scope.branch(),
                scope.environment(),
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

    List<GitLabFlowContextCandidate> toFlowContextCandidates(List<pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileCandidate> candidates) {
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
            return new FileOutline(null, List.of(), List.of(), List.of(), List.of());
        }

        var packageName = extractPackageName(content);
        var imports = limitList(deduplicate(extractMatches(content, IMPORT_PATTERN)), MAX_IMPORTS);
        var classes = limitList(deduplicate(extractMatches(content, CLASS_PATTERN)), MAX_CLASSES);
        var annotations = limitList(deduplicate(extractMatches(content, ANNOTATION_PATTERN)), MAX_ANNOTATIONS);
        var methodSignatures = limitList(deduplicate(extractMethodSignatures(content)), MAX_METHOD_SIGNATURES);

        return new FileOutline(
                packageName,
                imports,
                classes,
                annotations,
                methodSignatures
        );
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

    List<String> seedClassReferenceKeywords(String className) {
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

    private List<String> limitList(List<String> values, int maxItems) {
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

    private List<String> seedKeywords(String... values) {
        var seededValues = new ArrayList<String>();

        for (var value : values) {
            seededValues.add(value);
        }

        return seededValues;
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

    private List<String> extractMethodSignatures(String content) {
        var signatures = new ArrayList<String>();
        StringBuilder currentSignature = null;
        var parenthesisBalance = 0;

        for (var rawLine : content.split("\\R")) {
            var line = stripInlineComment(rawLine);
            if (!StringUtils.hasText(line)) {
                continue;
            }

            if (currentSignature == null) {
                if (!looksLikeMethodSignatureStart(line)) {
                    continue;
                }

                currentSignature = new StringBuilder(line.trim());
                parenthesisBalance = parenthesisBalance(line);
            } else {
                currentSignature.append(' ').append(line.trim());
                parenthesisBalance += parenthesisBalance(line);
            }

            if (parenthesisBalance <= 0 && endsMethodSignature(line)) {
                var normalizedSignature = normalizeMethodSignature(currentSignature.toString());
                if (StringUtils.hasText(normalizedSignature)) {
                    signatures.add(normalizedSignature);
                }

                currentSignature = null;
                parenthesisBalance = 0;

                if (signatures.size() >= MAX_METHOD_SIGNATURES) {
                    break;
                }
            }
        }

        if (currentSignature != null && signatures.size() < MAX_METHOD_SIGNATURES) {
            var normalizedSignature = normalizeMethodSignature(currentSignature.toString());
            if (StringUtils.hasText(normalizedSignature)) {
                signatures.add(normalizedSignature);
            }
        }

        return signatures;
    }

    private boolean looksLikeMethodSignatureStart(String line) {
        var trimmed = line.trim();
        if (!StringUtils.hasText(trimmed)
                || trimmed.startsWith("@")
                || trimmed.startsWith("//")
                || trimmed.startsWith("/*")
                || trimmed.startsWith("*")
                || !trimmed.contains("(")
                || trimmed.contains("->")) {
            return false;
        }

        var normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("if(")
                || normalized.startsWith("if (")
                || normalized.startsWith("for(")
                || normalized.startsWith("for (")
                || normalized.startsWith("while(")
                || normalized.startsWith("while (")
                || normalized.startsWith("switch(")
                || normalized.startsWith("switch (")
                || normalized.startsWith("catch(")
                || normalized.startsWith("catch (")
                || normalized.startsWith("return ")
                || normalized.startsWith("throw ")
                || normalized.startsWith("new ")
                || normalized.startsWith("do ")
                || normalized.startsWith("else ")
                || normalized.startsWith("case ")) {
            return false;
        }

        return METHOD_SIGNATURE_START_PATTERN.matcher(trimmed).find();
    }

    private int parenthesisBalance(String line) {
        var balance = 0;

        for (var index = 0; index < line.length(); index++) {
            var character = line.charAt(index);
            if (character == '(') {
                balance++;
            } else if (character == ')') {
                balance--;
            }
        }

        return balance;
    }

    private boolean endsMethodSignature(String line) {
        var trimmed = line.trim();
        return trimmed.endsWith("{")
                || trimmed.endsWith(";")
                || trimmed.endsWith(")")
                || trimmed.contains(" throws ");
    }

    private String normalizeMethodSignature(String signature) {
        var normalized = signature;
        var braceIndex = normalized.indexOf('{');
        if (braceIndex >= 0) {
            normalized = normalized.substring(0, braceIndex);
        }

        var semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex >= 0) {
            normalized = normalized.substring(0, semicolonIndex);
        }

        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized.contains("(") ? normalized : null;
    }

    private String stripInlineComment(String rawLine) {
        if (rawLine == null) {
            return "";
        }

        var commentIndex = rawLine.indexOf("//");
        var withoutComment = commentIndex >= 0 ? rawLine.substring(0, commentIndex) : rawLine;
        return withoutComment.trim();
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

    private String safeValue(String value) {
        return value != null ? value : "";
    }

    record FileOutline(
            String packageName,
            List<String> imports,
            List<String> classes,
            List<String> annotations,
            List<String> methodSignatures
    ) {
    }

}
