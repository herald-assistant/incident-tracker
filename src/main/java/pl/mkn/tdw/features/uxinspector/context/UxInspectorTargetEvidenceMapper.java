package pl.mkn.tdw.features.uxinspector.context;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.List;

@Component
public class UxInspectorTargetEvidenceMapper {
    public List<AnalysisEvidenceSection> map(UxInspectorTargetContext context) {
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        attributes.add(new AnalysisEvidenceAttribute("status", context.status().name()));
        attributes.add(new AnalysisEvidenceAttribute("viewId", context.view().viewId()));
        attributes.add(new AnalysisEvidenceAttribute("sourceRevision", context.sourceRevision().revision()));
        attributes.add(new AnalysisEvidenceAttribute("candidateCount", Integer.toString(context.candidates().size())));
        if (!context.candidates().isEmpty()) {
            var top = context.candidates().get(0);
            attributes.add(new AnalysisEvidenceAttribute("topCandidate", top.componentId()));
            attributes.add(new AnalysisEvidenceAttribute("topScore", Integer.toString(top.score())));
            attributes.add(new AnalysisEvidenceAttribute("matchReasons", String.join(", ", top.matchReasons())));
        }
        if (context.sourceBinding() != null) {
            attributes.add(new AnalysisEvidenceAttribute("componentSymbol", context.sourceBinding().componentSymbol()));
            attributes.add(new AnalysisEvidenceAttribute("sourceReference", context.sourceBinding().sourceReference()));
            attributes.add(new AnalysisEvidenceAttribute("bindingCount",
                    Integer.toString(context.sourceBinding().elementBindings().size())));
        }
        return List.of(new AnalysisEvidenceSection("ux-inspector", "target-resolution",
                List.of(new AnalysisEvidenceItem("Resolved browser target", attributes))));
    }
}
