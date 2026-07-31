package pl.mkn.tdw.features.runtimeconfigurationverification.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationAiAssessment;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.report.RuntimeConfigurationReportMapper;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

@Service
@RequiredArgsConstructor
public class RuntimeConfigurationAiAssessmentService {

    private final RuntimeConfigurationAiResponseParser responseParser;
    private final RuntimeConfigurationAgreementEvaluator agreementEvaluator;
    private final RuntimeConfigurationCombinedStatusEvaluator statusEvaluator;
    private final RuntimeConfigurationReportMapper reportMapper;

    public RuntimeConfigurationAiAssessment assess(
            String assistantContent,
            RuntimeConfigurationDeterministicContext deterministic,
            RuntimeConfigurationDeepContext deepContext,
            AnalysisReport reportScaffold,
            AnalysisReport aiReport
    ) {
        var opinion = responseParser.parse(assistantContent, deterministic, deepContext);
        return new RuntimeConfigurationAiAssessment(
                opinion,
                agreementEvaluator.evaluate(deterministic, opinion),
                statusEvaluator.evaluate(deterministic, opinion),
                reportMapper.merge(reportScaffold, aiReport, opinion)
        );
    }
}
