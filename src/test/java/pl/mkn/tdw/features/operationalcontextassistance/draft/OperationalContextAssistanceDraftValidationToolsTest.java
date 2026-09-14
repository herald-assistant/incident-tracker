package pl.mkn.tdw.features.operationalcontextassistance.draft;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceMode;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperationalContextAssistanceDraftValidationToolsTest {

    private final OperationalContextAssistanceDraftPreflight preflight = mock(OperationalContextAssistanceDraftPreflight.class);
    private final OperationalContextAssistanceDraftValidationTools tools =
            new OperationalContextAssistanceDraftValidationTools(preflight);

    @Test
    void createsTheFeatureOwnedCallbackWithoutAnApplicationProvider() {
        var provider = org.springframework.ai.tool.method.MethodToolCallbackProvider.builder()
                .toolObjects(tools).build();

        assertThat(provider.getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly(OperationalContextAssistanceDraftValidationTools.NAME);
    }

    @Test
    void usesTheHiddenDigestAndTheSourceRefsReadDuringTheSession() {
        var source = new GitLabRepositoryToolScope(
                "CRM", "crm-contact-api", "main", "1234567890abcdef1234567890abcdef12345678");
        var fileRef = source.recordRead(new GitLabRepositoryToolScope.Target(
                "CRM", "crm-contact-api", "main", source.selectedCommit()), "README.md");
        var session = new OperationalContextAssistanceDraftValidationTools.ValidationSession(
                "crm-digest", scope(), source);
        var expected = new OperationalContextAssistanceDraftPreflight.Result(true, false, List.of());
        when(preflight.validate(eq("{}"), any(), eq("crm-digest"))).thenReturn(expected);

        var actual = tools.validateDraft("{}", new ToolContext(Map.of(
                OperationalContextAssistanceDraftValidationTools.CONTEXT_KEY, session)));

        assertThat(actual).isEqualTo(expected);
        var captured = org.mockito.ArgumentCaptor.forClass(OperationalContextAssistanceDraftScope.class);
        verify(preflight).validate(eq("{}"), captured.capture(), eq("crm-digest"));
        assertThat(captured.getValue().allowedSourceRefs())
                .containsExactlyInAnyOrder("operator:description", fileRef);
    }

    @Test
    void worksWithoutGitLabAndRejectsMissingSessionContext() {
        var session = new OperationalContextAssistanceDraftValidationTools.ValidationSession(
                "crm-digest", scope(), null);
        var expected = new OperationalContextAssistanceDraftPreflight.Result(true, false, List.of());
        when(preflight.validate(eq("{}"), any(), eq("crm-digest"))).thenReturn(expected);

        assertThat(tools.validateDraft("{}", new ToolContext(Map.of(
                OperationalContextAssistanceDraftValidationTools.CONTEXT_KEY, session))))
                .isEqualTo(expected);
        assertThat(tools.validateDraft("{}", new ToolContext(Map.of())).valid()).isFalse();
        verify(preflight).validate(eq("{}"), eq(scope()), eq("crm-digest"));
    }

    @Test
    void missingHiddenContextNeverCallsPreflight() {
        var result = tools.validateDraft("{}", new ToolContext(Map.of()));

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(OperationalContextAssistanceDraftPreflight.Issue::pointer)
                .containsExactly("/draft");
        verifyNoInteractions(preflight);
    }

    private OperationalContextAssistanceDraftScope scope() {
        return new OperationalContextAssistanceDraftScope(
                OperationalContextAssistanceMode.CREATE_AREA, null, null, Set.of("operator:description"));
    }
}
