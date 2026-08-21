package pl.mkn.tdw.features.uiexplorer.ai.preparation;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRenderedArtifact;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UiExplorerPromptPreparationEvidenceMapperTest {

    @Test
    void shouldExposeStronglyAnonymizedCrmArtifactMetadataWithoutDuplicatingContent() {
        var section = new UiExplorerPromptPreparationEvidenceMapper().map(List.of(new CopilotRenderedArtifact(
                "ui-explorer/synthetic-crm-source-slices.md",
                "Synthetic CRM component and dependency slices",
                "ui-explorer",
                "screen-source-slices",
                3,
                "application/json",
                "{\"syntheticCrmContact\":true}"
        )));

        assertThat(section.provider()).isEqualTo("ui-explorer");
        assertThat(section.category()).isEqualTo("ai-artifacts");
        assertThat(section.items()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("ui-explorer/synthetic-crm-source-slices.md");
            assertThat(item.attributes()).extracting(attribute -> attribute.name())
                    .containsExactly("role", "category", "mimeType", "itemCount", "characterCount");
            assertThat(item.attributes()).extracting(attribute -> attribute.value())
                    .doesNotContain("{\"syntheticCrmContact\":true}");
        });
    }
}
