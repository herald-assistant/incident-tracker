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

        addAttribute(attributes, ElasticLogEvidenceView.ATTRIBUTE_TIMESTAMP, entry.timestamp());
        addAttribute(attributes, ElasticLogEvidenceView.ATTRIBUTE_LEVEL, entry.level());
        addAttribute(attributes, ElasticLogEvidenceView.ATTRIBUTE_SERVICE_NAME, entry.serviceName());
        addAttribute(attributes, ElasticLogEvidenceView.ATTRIBUTE_CLASS_NAME, entry.className());
        addAttribute(attributes, ElasticLogEvidenceView.ATTRIBUTE_MESSAGE, entry.message());
        addAttribute(attributes, ElasticLogEvidenceView.ATTRIBUTE_EXCEPTION, entry.exception());
        addAttribute(attributes, ElasticLogEvidenceView.ATTRIBUTE_THREAD, entry.thread());
        addAttribute(attributes, ElasticLogEvidenceView.ATTRIBUTE_SPAN_ID, entry.spanId());
        addAttribute(attributes, ElasticLogEvidenceView.ATTRIBUTE_NAMESPACE, entry.namespace());
        addAttribute(attributes, ElasticLogEvidenceView.ATTRIBUTE_POD_NAME, entry.podName());
        addAttribute(attributes, ElasticLogEvidenceView.ATTRIBUTE_CONTAINER_NAME, entry.containerName());
        addAttribute(attributes, ElasticLogEvidenceView.ATTRIBUTE_CONTAINER_IMAGE, entry.containerImage());

        if (entry.messageTruncated()) {
            attributes.add(new AnalysisEvidenceAttribute(ElasticLogEvidenceView.ATTRIBUTE_MESSAGE_TRUNCATED, "true"));
        }
        if (entry.exceptionTruncated()) {
            attributes.add(new AnalysisEvidenceAttribute(ElasticLogEvidenceView.ATTRIBUTE_EXCEPTION_TRUNCATED, "true"));
        }

        return List.copyOf(attributes);
    }

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, value));
        }
    }

}
