package pl.mkn.tdw.features.operationalcontextassistance.draft;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogBatchMutationPreview;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogConditionalBatchCommand;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogFieldError;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceException;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextSnapshot;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationalContextAssistanceDraftPreflightTest {

    private final OperationalContextAssistanceDraftParser parser = mock(OperationalContextAssistanceDraftParser.class);
    private final OperationalContextCatalogMaintenanceService maintenance = mock(OperationalContextCatalogMaintenanceService.class);
    private final OperationalContextPort catalogPort = mock(OperationalContextPort.class);
    private final OperationalContextAssistanceDraftPreflight preflight =
            new OperationalContextAssistanceDraftPreflight(parser, maintenance, catalogPort);

    @Test
    void validatesTheCompleteCandidateWithoutPublishing() {
        var draft = draft();
        var snapshot = mock(OperationalContextSnapshot.class);
        when(snapshot.contentDigest()).thenReturn("crm-digest");
        when(catalogPort.currentSnapshot()).thenReturn(snapshot);
        when(maintenance.previewAcceptedBatch(any(OperationalContextCatalogConditionalBatchCommand.class)))
                .thenReturn(new OperationalContextCatalogBatchMutationPreview(List.of(), "crm-digest", "candidate", List.of()));

        var result = preflight.validateParsed(draft, "crm-digest");

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
        var command = org.mockito.ArgumentCaptor.forClass(OperationalContextCatalogConditionalBatchCommand.class);
        verify(maintenance).previewAcceptedBatch(command.capture());
        assertThat(command.getValue().mutations()).hasSize(1);
        assertThat(command.getValue().mutations().get(0).id()).isEqualTo("crm-contact-status");
        verify(maintenance, never()).applyAcceptedBatch(any());
    }

    @Test
    void returnsTheExactMutationPointerForInvalidReferences() {
        var snapshot = mock(OperationalContextSnapshot.class);
        when(snapshot.contentDigest()).thenReturn("crm-digest");
        when(catalogPort.currentSnapshot()).thenReturn(snapshot);
        when(maintenance.previewAcceptedBatch(any(OperationalContextCatalogConditionalBatchCommand.class)))
                .thenThrow(OperationalContextCatalogMaintenanceException.validation("Invalid reference", List.of(
                        new OperationalContextCatalogFieldError(
                                "/mutations/0/payload/relatedTerms/1", "Referenced entity does not exist"))));

        var result = preflight.validateParsed(draft(), "crm-digest");

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).containsExactly(new OperationalContextAssistanceDraftPreflight.Issue(
                "/mutations/0/payload/relatedTerms/1", "Referenced entity does not exist"));
    }

    @Test
    void rejectsAChangedCatalogBeforeAssessingTheCandidate() {
        var snapshot = mock(OperationalContextSnapshot.class);
        when(snapshot.contentDigest()).thenReturn("new-digest");
        when(catalogPort.currentSnapshot()).thenReturn(snapshot);

        var result = preflight.validateParsed(draft(), "crm-digest");

        assertThat(result.valid()).isFalse();
        assertThat(result.stale()).isTrue();
        verify(maintenance, never()).previewAcceptedBatch(any());
    }

    @Test
    void reportsStrictDraftParsingFailure() {
        var scope = new OperationalContextAssistanceDraftScope(
                pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceMode.CREATE_AREA,
                null, null, java.util.Set.of("operator:description"));
        when(parser.parse(eq("not-json"), eq(scope)))
                .thenThrow(new OperationalContextAssistanceDraftParseException("Response is not strict JSON"));

        var result = preflight.validate("not-json", scope, "crm-digest");

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).containsExactly(new OperationalContextAssistanceDraftPreflight.Issue(
                "/draft", "Response is not strict JSON"));
        verify(maintenance, never()).previewAcceptedBatch(any());
    }

    private OperationalContextAssistanceDraft draft() {
        return new OperationalContextAssistanceDraft(List.of(new OperationalContextAssistanceDraft.Proposal(
                OperationalContextAssistanceDraft.Operation.CREATE, "glossary-term", "crm-contact-status",
                List.of(new OperationalContextAssistanceDraft.FieldChange(
                        "term", null, "Contact status", "CRM domain definition",
                        OperationalContextAssistanceDraft.Basis.USER_STATEMENT,
                        List.of("operator:description"), OperationalContextAssistanceDraft.Confidence.HIGH, false)),
                OperationalContextAssistanceDraft.Confidence.HIGH, false, List.of())), List.of());
    }
}
