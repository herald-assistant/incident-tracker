package pl.mkn.incidenttracker.analysis.mcp.gitlab;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositorySearchQuery;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class GitLabMcpTools {

    private static final int DEFAULT_MAX_CHARACTERS = 4_000;

    private final GitLabRepositoryPort gitLabRepositoryPort;

    @Tool(
            name = "gitlab_search_repository_candidates",
            description = "Search GitLab repository files in a specific group and branch using candidate project names, operation names, and keywords inferred from logs, traces, or prior analysis."
    )
    public GitLabSearchRepositoryCandidatesToolResponse searchRepositoryCandidates(
            @ToolParam(required = false, description = "Optional correlationId of the analyzed incident.")
            String correlationId,
            @ToolParam(description = "GitLab group path, for example platform/backend.")
            String group,
            @ToolParam(description = "GitLab branch or ref, for example main or feature/INC-123.")
            String branch,
            @ToolParam(required = false, description = "One or more candidate GitLab project paths inside the group. Relative subgroup segments are allowed, for example PROCESSES/CREDIT_AGREEMENT_PROCESS.")
            List<String> projectNames,
            @ToolParam(required = false, description = "Operation names inferred from traces.")
            List<String> operationNames,
            @ToolParam(required = false, description = "Keywords inferred from logs, traces, or the current hypothesis.")
            List<String> keywords
    ) {
        var safeProjectNames = defaultList(projectNames);
        var safeOperationNames = defaultList(operationNames);
        var safeKeywords = defaultList(keywords);

        log.info(
                "Tool request [{}] correlationId={} group={} branch={} projectNames={} operationNames={} keywords={}",
                "gitlab_search_repository_candidates",
                correlationId,
                group,
                branch,
                abbreviateList(safeProjectNames),
                abbreviateList(safeOperationNames),
                abbreviateList(safeKeywords)
        );

        var query = new GitLabRepositorySearchQuery(
                correlationId,
                group,
                branch,
                safeProjectNames,
                safeOperationNames,
                safeKeywords
        );
        var candidates = gitLabRepositoryPort.searchCandidateFiles(query);

        log.info(
                "Tool result [{}] candidateCount={} candidatePaths={}",
                "gitlab_search_repository_candidates",
                candidates.size(),
                abbreviateList(candidates.stream()
                        .map(candidate -> candidate.projectName() + ":" + candidate.filePath())
                        .toList())
        );

        return new GitLabSearchRepositoryCandidatesToolResponse(
                candidates
        );
    }

    @Tool(
            name = "gitlab_read_repository_file",
            description = "Read a file from the GitLab repository by group, project name, branch, and repository path. Use maxCharacters to limit large responses."
    )
    public GitLabReadRepositoryFileToolResponse readRepositoryFile(
            @ToolParam(description = "GitLab group path, for example platform/backend.")
            String group,
            @ToolParam(description = "GitLab project path inside the group, for example checkout-service or PROCESSES/CREDIT_AGREEMENT_PROCESS.")
            String projectName,
            @ToolParam(description = "GitLab branch or ref, for example main or feature/INC-123.")
            String branch,
            @ToolParam(description = "Repository file path, for example src/main/java/pl/mkn/checkout/CheckoutHttpClient.java.")
            String filePath,
            @ToolParam(required = false, description = "Maximum number of characters to return. Defaults to 4000.")
            Integer maxCharacters
    ) {
        var effectiveMaxCharacters = maxCharacters != null ? maxCharacters : DEFAULT_MAX_CHARACTERS;

        log.info(
                "Tool request [{}] group={} projectName={} branch={} filePath={} maxCharacters={}",
                "gitlab_read_repository_file",
                group,
                projectName,
                branch,
                filePath,
                effectiveMaxCharacters
        );

        var fileContent = gitLabRepositoryPort.readFile(
                group,
                projectName,
                branch,
                filePath,
                effectiveMaxCharacters
        );

        log.info(
                "Tool result [{}] group={} projectName={} branch={} filePath={} returnedCharacters={} truncated={}",
                "gitlab_read_repository_file",
                fileContent.group(),
                fileContent.projectName(),
                fileContent.branch(),
                fileContent.filePath(),
                fileContent.content() != null ? fileContent.content().length() : 0,
                fileContent.truncated()
        );

        return new GitLabReadRepositoryFileToolResponse(
                fileContent.group(),
                fileContent.projectName(),
                fileContent.branch(),
                fileContent.filePath(),
                fileContent.content(),
                fileContent.truncated()
        );
    }

    @Tool(
            name = "gitlab_read_repository_file_chunk",
            description = "Read only a selected line range from a GitLab repository file by group, project name, branch, and repository path. Line numbers are 1-based and inclusive."
    )
    public GitLabReadRepositoryFileChunkToolResponse readRepositoryFileChunk(
            @ToolParam(description = "GitLab group path, for example platform/backend.")
            String group,
            @ToolParam(description = "GitLab project path inside the group, for example checkout-service or PROCESSES/CREDIT_AGREEMENT_PROCESS.")
            String projectName,
            @ToolParam(description = "GitLab branch or ref, for example main or feature/INC-123.")
            String branch,
            @ToolParam(description = "Repository file path, for example src/main/java/pl/mkn/checkout/CheckoutHttpClient.java.")
            String filePath,
            @ToolParam(description = "First line to return. Uses 1-based inclusive numbering.")
            int startLine,
            @ToolParam(description = "Last line to return. Uses 1-based inclusive numbering.")
            int endLine,
            @ToolParam(required = false, description = "Maximum number of characters to return. Defaults to 4000.")
            Integer maxCharacters
    ) {
        var effectiveMaxCharacters = maxCharacters != null ? maxCharacters : DEFAULT_MAX_CHARACTERS;

        log.info(
                "Tool request [{}] group={} projectName={} branch={} filePath={} requestedStartLine={} requestedEndLine={} maxCharacters={}",
                "gitlab_read_repository_file_chunk",
                group,
                projectName,
                branch,
                filePath,
                startLine,
                endLine,
                effectiveMaxCharacters
        );

        var fileChunk = gitLabRepositoryPort.readFileChunk(
                group,
                projectName,
                branch,
                filePath,
                startLine,
                endLine,
                effectiveMaxCharacters
        );

        log.info(
                "Tool result [{}] group={} projectName={} branch={} filePath={} returnedStartLine={} returnedEndLine={} totalLines={} returnedCharacters={} truncated={}",
                "gitlab_read_repository_file_chunk",
                fileChunk.group(),
                fileChunk.projectName(),
                fileChunk.branch(),
                fileChunk.filePath(),
                fileChunk.returnedStartLine(),
                fileChunk.returnedEndLine(),
                fileChunk.totalLines(),
                fileChunk.content() != null ? fileChunk.content().length() : 0,
                fileChunk.truncated()
        );

        return new GitLabReadRepositoryFileChunkToolResponse(
                fileChunk.group(),
                fileChunk.projectName(),
                fileChunk.branch(),
                fileChunk.filePath(),
                fileChunk.requestedStartLine(),
                fileChunk.requestedEndLine(),
                fileChunk.returnedStartLine(),
                fileChunk.returnedEndLine(),
                fileChunk.totalLines(),
                fileChunk.content(),
                fileChunk.truncated()
        );
    }

    private List<String> defaultList(List<String> values) {
        return values != null ? values : List.of();
    }

    private String abbreviateList(List<String> values) {
        if (values.isEmpty()) {
            return "[]";
        }

        var maxItems = Math.min(values.size(), 3);
        var preview = values.subList(0, maxItems);
        return values.size() > maxItems
                ? preview + " ... (" + values.size() + " items)"
                : preview.toString();
    }

}
