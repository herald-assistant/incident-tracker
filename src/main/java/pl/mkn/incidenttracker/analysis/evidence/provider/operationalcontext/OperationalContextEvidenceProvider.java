package pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextPort;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextProperties;
import pl.mkn.incidenttracker.analysis.adapter.operationalcontext.OperationalContextQuery;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisStepPhase;
import pl.mkn.incidenttracker.analysis.evidence.provider.dynatrace.DynatraceRuntimeEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic.GitLabResolvedCodeEvidenceView;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class OperationalContextEvidenceProvider implements AnalysisEvidenceProvider {

    private final OperationalContextProperties properties;
    private final OperationalContextPort operationalContextPort;
    private final OperationalContextCatalogMatcher catalogMatcher;
    private final OperationalContextEvidenceMapper evidenceMapper;

    @Override
    public AnalysisEvidenceSection collect(AnalysisContext context) {
        if (!properties.isEnabled()) {
            return evidenceMapper.emptySection();
        }

        try {
            var catalog = operationalContextPort.loadContext(OperationalContextQuery.all());
            var signals = OperationalContextIncidentSignals.from(context);
            if (signals.isEmpty()) {
                return evidenceMapper.emptySection();
            }

            var matches = catalogMatcher.match(catalog, signals);
            return evidenceMapper.toEvidenceSection(matches, catalog);
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
        return OperationalContextEvidenceView.EVIDENCE_REFERENCE;
    }

    @Override
    public List<AnalysisEvidenceReference> consumedEvidence() {
        return List.of(
                ElasticLogEvidenceView.EVIDENCE_REFERENCE,
                DeploymentContextEvidenceView.EVIDENCE_REFERENCE,
                DynatraceRuntimeEvidenceView.EVIDENCE_REFERENCE,
                GitLabResolvedCodeEvidenceView.EVIDENCE_REFERENCE
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
