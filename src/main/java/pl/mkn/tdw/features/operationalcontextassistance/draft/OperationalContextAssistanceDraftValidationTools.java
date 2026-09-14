package pl.mkn.tdw.features.operationalcontextassistance.draft;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;

import java.util.LinkedHashSet;

@Component
@RequiredArgsConstructor
public class OperationalContextAssistanceDraftValidationTools {

    public static final String NAME = "operational_context_assistance_validate_draft";
    public static final String CONTEXT_KEY = "operationalContextAssistanceValidation";

    private final OperationalContextAssistanceDraftPreflight preflight;

    @Tool(name = NAME, description = """
            Validates one complete Operational Context assistance draft before the final response.
            Read-only: checks the strict draft contract and the complete candidate catalog, including
            references to entities created later in the same draft. Returns precise errors to correct.
            The pinned catalog and allowed source references come from hidden session context.
            This tool never publishes catalog changes.
            """)
    public OperationalContextAssistanceDraftPreflight.Result validateDraft(
            @ToolParam(description = "Complete proposed response as one JSON object with proposals and visibilityLimits.")
            String draftJson,
            ToolContext toolContext
    ) {
        var context = toolContext != null && toolContext.getContext() != null
                ? toolContext.getContext().get(CONTEXT_KEY) : null;
        if (!(context instanceof ValidationSession session)) {
            return new OperationalContextAssistanceDraftPreflight.Result(false, false,
                    java.util.List.of(new OperationalContextAssistanceDraftPreflight.Issue(
                            "/draft", "Validation session is unavailable")));
        }
        return preflight.validate(draftJson, session.currentScope(), session.catalogDigest());
    }

    public record ValidationSession(
            String catalogDigest,
            OperationalContextAssistanceDraftScope initialScope,
            GitLabRepositoryToolScope sourceScope
    ) {
        public ValidationSession {
            if (catalogDigest == null || catalogDigest.isBlank() || initialScope == null) {
                throw new IllegalArgumentException("Pinned catalog and draft scope are required.");
            }
        }

        public OperationalContextAssistanceDraftScope currentScope() {
            var refs = new LinkedHashSet<>(initialScope.allowedSourceRefs());
            if (sourceScope != null) {
                refs.addAll(sourceScope.readSourceRefs());
            }
            return new OperationalContextAssistanceDraftScope(
                    initialScope.mode(), initialScope.targetType(), initialScope.targetId(), refs,
                    initialScope.selectedRepositoryGit(), initialScope.repositoryFacts(),
                    initialScope.selectedScopeIds());
        }
    }
}
