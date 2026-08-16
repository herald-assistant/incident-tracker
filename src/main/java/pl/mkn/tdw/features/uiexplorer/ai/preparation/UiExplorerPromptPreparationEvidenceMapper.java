package pl.mkn.tdw.features.uiexplorer.ai.preparation;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.List;

@Component
public class UiExplorerPromptPreparationEvidenceMapper {

    public static final String PROVIDER = "ui-explorer";
    public static final String CATEGORY = "ai-artifacts";

    public AnalysisEvidenceSection map(List<CopilotRenderedArtifact> artifacts) {
        var items = (artifacts != null ? artifacts : List.<CopilotRenderedArtifact>of()).stream()
                .map(this::artifactItem)
                .toList();
        return new AnalysisEvidenceSection(PROVIDER, CATEGORY, items);
    }

    private AnalysisEvidenceItem artifactItem(CopilotRenderedArtifact artifact) {
        return new AnalysisEvidenceItem(
                artifact.displayName(),
                List.of(
                        attribute("role", artifact.role()),
                        attribute("category", artifact.category()),
                        attribute("mimeType", artifact.mimeType()),
                        attribute("itemCount", artifact.itemCount()),
                        attribute("characterCount", artifact.content() != null ? artifact.content().length() : 0)
                )
        );
    }

    private AnalysisEvidenceAttribute attribute(String name, Object value) {
        return new AnalysisEvidenceAttribute(name, value != null ? value.toString() : "");
    }
}
