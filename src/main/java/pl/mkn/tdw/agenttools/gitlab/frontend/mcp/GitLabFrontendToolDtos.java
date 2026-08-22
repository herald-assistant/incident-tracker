package pl.mkn.tdw.agenttools.gitlab.frontend.mcp;

import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceFile;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceResponse;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteChildReference;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendEffectiveRouteChain;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendGraphDiagnostic;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteNode;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptDownstreamReference;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolCandidate;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceResponse;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptTemplateBinding;

import java.util.List;

public final class GitLabFrontendToolDtos {

    private GitLabFrontendToolDtos() {
    }

    public record RouteBranchSliceToolResponse(
            String sliceRef,
            String sourceRevision,
            String status,
            GitLabFrontendRouteNode screenNode,
            GitLabFrontendEffectiveRouteChain effectiveRouteChain,
            List<GitLabAngularRouteBranchSliceFile> files,
            List<GitLabAngularRouteChildReference> childRoutes,
            int sourceCharacters,
            int returnedCharacters,
            int savedCharacters,
            int omittedImportCount,
            int omittedSiblingRouteCount,
            boolean truncated,
            List<String> limitations,
            List<GitLabFrontendGraphDiagnostic> diagnostics
    ) {
        static RouteBranchSliceToolResponse from(
                String sliceRef,
                GitLabAngularRouteBranchSliceResponse response
        ) {
            return new RouteBranchSliceToolResponse(
                    sliceRef,
                    response.sourceRevision().commitId(),
                    response.status(),
                    response.screenNode(),
                    response.effectiveRouteChain(),
                    response.files(),
                    response.childRoutes(),
                    response.sourceCharacters(),
                    response.returnedCharacters(),
                    response.savedCharacters(),
                    response.omittedImportCount(),
                    response.omittedSiblingRouteCount(),
                    response.truncated(),
                    response.limitations(),
                    response.diagnostics()
            );
        }
    }

    public record TypeScriptSymbolSliceToolResponse(
            String sliceRef,
            String filePath,
            String status,
            String declaringTypeName,
            int lineStart,
            int lineEnd,
            int totalLines,
            int sourceCharacters,
            String templatePath,
            int templateCharacters,
            List<GitLabTypeScriptTemplateBinding> templateBindings,
            String content,
            int returnedCharacters,
            int savedCharacters,
            boolean truncated,
            List<String> includedImports,
            List<String> includedFields,
            List<GitLabTypeScriptSymbolCandidate> entrySymbols,
            List<GitLabTypeScriptSymbolCandidate> includedSymbols,
            int omittedImportCount,
            int omittedFieldCount,
            int omittedSymbolCount,
            List<GitLabTypeScriptDownstreamReference> downstreamReferences,
            List<GitLabTypeScriptSymbolCandidate> candidates,
            List<String> limitations
    ) {
        static TypeScriptSymbolSliceToolResponse from(
                String sliceRef,
                GitLabTypeScriptSymbolSliceResponse response
        ) {
            return new TypeScriptSymbolSliceToolResponse(
                    sliceRef,
                    response.filePath(),
                    response.status(),
                    response.declaringTypeName(),
                    response.lineStart(),
                    response.lineEnd(),
                    response.totalLines(),
                    response.sourceCharacters(),
                    response.templatePath(),
                    response.templateCharacters(),
                    response.templateBindings(),
                    response.content(),
                    response.returnedCharacters(),
                    response.savedCharacters(),
                    response.truncated(),
                    response.includedImports(),
                    response.includedFields(),
                    response.entrySymbols(),
                    response.includedSymbols(),
                    response.omittedImportCount(),
                    response.omittedFieldCount(),
                    response.omittedSymbolCount(),
                    response.downstreamReferences(),
                    response.candidates(),
                    response.limitations()
            );
        }
    }

}
