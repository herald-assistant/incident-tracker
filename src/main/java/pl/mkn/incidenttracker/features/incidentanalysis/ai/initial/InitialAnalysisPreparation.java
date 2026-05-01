package pl.mkn.incidenttracker.features.incidentanalysis.ai.initial;

public interface InitialAnalysisPreparation extends AutoCloseable {

    String providerName();

    String correlationId();

    String prompt();

    @Override
    default void close() {
    }
}

