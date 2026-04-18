package pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogEntry;
import pl.mkn.incidenttracker.analysis.adapter.elasticsearch.ElasticLogPort;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisStepPhase;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ElasticLogEvidenceProvider implements AnalysisEvidenceProvider {

    private final ElasticLogPort elasticLogPort;

    @Override
    public AnalysisEvidenceSection collect(AnalysisContext context) {
        var items = new ArrayList<AnalysisEvidenceItem>();

        for (var entry : elasticLogPort.findLogEntries(context.correlationId())) {
            items.add(new AnalysisEvidenceItem(
                    buildTitle(entry),
                    buildAttributes(entry)
            ));
        }

        return new AnalysisEvidenceSection("elasticsearch", "logs", items);
    }

    @Override
    public AnalysisEvidenceReference producedEvidence() {
        return new AnalysisEvidenceReference("elasticsearch", "logs");
    }

    @Override
    public String stepCode() {
        return "ELASTICSEARCH_LOGS";
    }

    @Override
    public String stepLabel() {
        return "Zbieranie logow z Elasticsearch";
    }

    @Override
    public AnalysisStepPhase stepPhase() {
        return AnalysisStepPhase.SOURCE;
    }

    private String buildTitle(ElasticLogEntry entry) {
        var level = StringUtils.hasText(entry.level()) ? entry.level() : "UNKNOWN";
        var serviceName = StringUtils.hasText(entry.serviceName()) ? entry.serviceName() : "application";
        return level + " " + serviceName + " log entry";
    }

    private List<AnalysisEvidenceAttribute> buildAttributes(ElasticLogEntry entry) {
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();

        addAttribute(attributes, "timestamp", entry.timestamp());
        addAttribute(attributes, "level", entry.level());
        addAttribute(attributes, "serviceName", entry.serviceName());
        addAttribute(attributes, "className", entry.className());
        addAttribute(attributes, "message", entry.message());
        addAttribute(attributes, "exception", entry.exception());
        addAttribute(attributes, "thread", entry.thread());
        addAttribute(attributes, "spanId", entry.spanId());
        addAttribute(attributes, "namespace", entry.namespace());
        addAttribute(attributes, "podName", entry.podName());
        addAttribute(attributes, "containerName", entry.containerName());
        addAttribute(attributes, "containerImage", entry.containerImage());

        if (entry.messageTruncated()) {
            attributes.add(new AnalysisEvidenceAttribute("messageTruncated", "true"));
        }
        if (entry.exceptionTruncated()) {
            attributes.add(new AnalysisEvidenceAttribute("exceptionTruncated", "true"));
        }

        return List.copyOf(attributes);
    }

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, value));
        }
    }

}
