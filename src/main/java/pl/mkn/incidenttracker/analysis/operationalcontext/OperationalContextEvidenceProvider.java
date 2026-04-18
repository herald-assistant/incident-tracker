package pl.mkn.incidenttracker.analysis.operationalcontext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisStepPhase;
import pl.mkn.incidenttracker.analysis.evidence.view.DeploymentContextEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.view.ElasticLogEvidenceView;

import java.util.List;

@Component
@Slf4j
@Order(40)
@RequiredArgsConstructor
public class OperationalContextEvidenceProvider implements AnalysisEvidenceProvider {

    private final OperationalContextProperties properties;
    private final OperationalContextCatalogLoader catalogLoader;
    private final OperationalContextCatalogMatcher catalogMatcher;
    private final OperationalContextEvidenceMapper evidenceMapper;

    @Override
    public AnalysisEvidenceSection collect(AnalysisContext context) {
        if (!properties.isEnabled()) {
            return evidenceMapper.emptySection();
        }

        try {
            var catalog = catalogLoader.loadCatalog().orElse(null);
            if (catalog == null) {
                return evidenceMapper.emptySection();
            }

            var signals = OperationalContextIncidentSignals.from(context);
            if (signals.isEmpty()) {
                return evidenceMapper.emptySection();
            }

            var matches = catalogMatcher.match(catalog, signals);
            return evidenceMapper.toEvidenceSection(matches);
        } catch (RuntimeException exception) {
            log.warn(
                    "Operational context enrichment skipped correlationId={} reason={}",
                    context.correlationId(),
                    exception.getMessage()
            );
            log.debug("Operational context enrichment failure correlationId={}", context.correlationId(), exception);
            return evidenceMapper.emptySection();
        }
    }

    @Override
    public AnalysisEvidenceReference producedEvidence() {
        return new AnalysisEvidenceReference("operational-context", "matched-context");
    }

    @Override
    public List<AnalysisEvidenceReference> consumedEvidence() {
        return List.of(
                ElasticLogEvidenceView.EVIDENCE_REFERENCE,
                DeploymentContextEvidenceView.EVIDENCE_REFERENCE,
                new AnalysisEvidenceReference("dynatrace", "runtime-signals"),
                new AnalysisEvidenceReference("gitlab", "resolved-code")
        );
    }

    @Override
    public AnalysisStepPhase stepPhase() {
        return AnalysisStepPhase.ENRICHMENT;
    }

    @Override
    public String stepCode() {
        return "OPERATIONAL_CONTEXT";
    }

    @Override
    public String stepLabel() {
        return "Dopasowanie kontekstu operacyjnego";
    }

}
