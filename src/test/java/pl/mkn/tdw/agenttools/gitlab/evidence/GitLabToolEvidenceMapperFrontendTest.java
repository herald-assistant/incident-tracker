package pl.mkn.tdw.agenttools.gitlab.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.common.JsonPayloadReader;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_FRONTEND_ROUTE_BRANCH_SLICE;
import static pl.mkn.tdw.agenttools.gitlab.GitLabToolNames.READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE;

class GitLabToolEvidenceMapperFrontendTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GitLabToolEvidenceMapper mapper =
            new GitLabToolEvidenceMapper(objectMapper, new JsonPayloadReader(objectMapper));

    @Test
    void shouldCaptureFocusedCrmRouteAndSymbolSlicesWithoutRepositoryCoordinates() {
        var sink = new RecordingSink();

        mapper.capture(
                "crm-call-route",
                READ_FRONTEND_ROUTE_BRANCH_SLICE,
                "{\"sliceRef\":\"crm-contact-preferences\",\"reason\":\"Potwierdzenie routingu CRM.\"}",
                """
                        {
                          "sliceRef": "crm-contact-preferences",
                          "sourceRevision": "crm-commit-abc123",
                          "status": "RESOLVED",
                          "files": [{
                            "path": "apps/crm-agent/src/app/contact-preferences/crm-contact.routes.ts",
                            "content": "export const crmContactRoutes = [];"
                          }]
                        }
                        """,
                sink
        );
        mapper.capture(
                "crm-call-symbol",
                READ_FRONTEND_TYPESCRIPT_SYMBOL_SLICE,
                "{\"sliceRef\":\"component-crm-contact-preferences\",\"reason\":\"Potwierdzenie formularza CRM.\"}",
                """
                        {
                          "sliceRef": "component-crm-contact-preferences",
                          "filePath": "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.ts",
                          "status": "RESOLVED",
                          "lineStart": 12,
                          "content": "export class CrmContactPreferencesComponent {}"
                        }
                        """,
                sink
        );

        assertThat(sink.items).hasSize(2);
        assertThat(sink.items).allSatisfy(item -> {
            assertThat(item.attributes()).extracting("name")
                    .contains("filePath", "reason", "toolCallId", "toolName", "toolArguments", "content")
                    .doesNotContain("group", "projectName", "branch", "ref");
        });
        assertThat(sink.items.get(1).attributes()).anySatisfy(attribute -> {
            assertThat(attribute.name()).isEqualTo("startLine");
            assertThat(attribute.value()).isEqualTo("12");
        });
    }

    private static final class RecordingSink implements GitLabToolEvidenceSink {

        private final List<AnalysisEvidenceItem> items = new ArrayList<>();

        @Override
        public AnalysisEvidenceSection upsertItem(
                String provider,
                String category,
                String key,
                String orderNamespace,
                String fallbackKey,
                AnalysisEvidenceItem candidate,
                BiPredicate<AnalysisEvidenceItem, AnalysisEvidenceItem> keepExisting
        ) {
            items.add(candidate);
            return new AnalysisEvidenceSection(provider, category, List.copyOf(items));
        }

        @Override
        public AnalysisEvidenceSection appendItem(
                String provider,
                String category,
                String key,
                String orderNamespace,
                String fallbackKey,
                AnalysisEvidenceItem candidate
        ) {
            items.add(candidate);
            return new AnalysisEvidenceSection(provider, category, List.copyOf(items));
        }
    }
}
