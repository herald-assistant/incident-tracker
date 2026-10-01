package pl.mkn.tdw.shared.ai;

@FunctionalInterface
public interface AnalysisAiInvocationListener {
    AnalysisAiInvocationListener NO_OP = invocation -> {};
    void onInvocation(AnalysisAiInvocation invocation);
}
