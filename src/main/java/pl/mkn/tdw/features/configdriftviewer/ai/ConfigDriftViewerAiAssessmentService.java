package pl.mkn.tdw.features.configdriftviewer.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiAssessment;
import pl.mkn.tdw.features.configdriftviewer.ai.report.ConfigDriftViewerReportMapper;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

@Service
@RequiredArgsConstructor
public class ConfigDriftViewerAiAssessmentService {

    private final ConfigDriftViewerAiResponseParser responseParser;
    private final ConfigDriftViewerAgreementEvaluator agreementEvaluator;
    private final ConfigDriftViewerCombinedStatusEvaluator statusEvaluator;
    private final ConfigDriftViewerReportMapper reportMapper;

    public ConfigDriftViewerAiAssessment assess(
            String assistantContent,
            ConfigDriftViewerDeterministicContext deterministic,
            ConfigDriftViewerDeepContext deepContext,
            AnalysisReport reportScaffold,
            AnalysisReport aiReport
    ) {
        var opinion = responseParser.parse(assistantContent, deterministic, deepContext);
        return new ConfigDriftViewerAiAssessment(
                opinion,
                agreementEvaluator.evaluate(deterministic, opinion),
                statusEvaluator.evaluate(deterministic, opinion),
                reportMapper.merge(reportScaffold, aiReport, opinion)
        );
    }
}
