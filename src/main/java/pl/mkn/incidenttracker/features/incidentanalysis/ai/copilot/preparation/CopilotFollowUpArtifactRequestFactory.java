package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.chat.AnalysisAiChatRequest;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;

@Component
public class CopilotFollowUpArtifactRequestFactory {

    public InitialAnalysisRequest create(AnalysisAiChatRequest request) {
        var sections = new ArrayList<AnalysisEvidenceSection>();
        sections.addAll(request.evidenceSections());
        sections.addAll(request.toolEvidenceSections());

        return new InitialAnalysisRequest(
                request.correlationId(),
                request.environment(),
                request.gitLabBranch(),
                request.gitLabGroup(),
                sections,
                request.options()
        );
    }
}
