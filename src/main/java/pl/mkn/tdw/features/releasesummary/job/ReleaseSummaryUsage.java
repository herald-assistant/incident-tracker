package pl.mkn.tdw.features.releasesummary.job;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;
import java.util.Objects;
/** Feature total; unknown cost remains unknown, context reflects the latest turn. */
final class ReleaseSummaryUsage {
    private ReleaseSummaryUsage() {}
    static AnalysisAiUsage combine(AnalysisAiUsage a,AnalysisAiUsage b) {
        if(a==null)return b;if(b==null)return a;
        return new AnalysisAiUsage(a.inputTokens()+b.inputTokens(),a.outputTokens()+b.outputTokens(),
                sum(a.cacheReadTokens(),b.cacheReadTokens()),sum(a.cacheWriteTokens(),b.cacheWriteTokens()),
                a.totalTokens()+b.totalTokens(),a.aiCredits()==null||b.aiCredits()==null?null:a.aiCredits()+b.aiCredits(),
                a.apiDurationMs()+b.apiDurationMs(),a.apiCallCount()+b.apiCallCount(),
                Objects.equals(a.model(),b.model())?a.model():a.model()+", "+b.model(),
                b.contextTokenLimit(),b.contextCurrentTokens(),b.contextMessages(),sum(a.reasoningTokens(),b.reasoningTokens()));
    }
    private static Long sum(Long a,Long b){return a==null||b==null?null:a+b;}
}
