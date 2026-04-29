package pl.mkn.incidenttracker.analysis.ai.prepared;

public interface AnalysisAiPreparedAnalysis extends AutoCloseable {

    String providerName();

    String correlationId();

    String prompt();

    @Override
    default void close() {
    }
}

