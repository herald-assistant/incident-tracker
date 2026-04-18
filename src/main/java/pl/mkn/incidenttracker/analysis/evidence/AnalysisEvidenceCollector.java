package pl.mkn.incidenttracker.analysis.evidence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisEvidenceCollector {

    private final List<AnalysisEvidenceProvider> providers;

    public AnalysisContext collect(String correlationId) {
        return collect(correlationId, AnalysisEvidenceCollectionListener.NO_OP);
    }

    public AnalysisContext collect(String correlationId, AnalysisEvidenceCollectionListener listener) {
        var context = AnalysisContext.initialize(correlationId);

        for (var provider : providers) {
            listener.onProviderStarted(provider, context);
            var section = provider.collect(context);
            context = context.withSection(section);
            listener.onProviderCompleted(provider, section, context);
        }

        return context;
    }

    public List<AnalysisEvidenceProviderDescriptor> providerDescriptors() {
        return providers.stream()
                .map(AnalysisEvidenceProvider::descriptor)
                .toList();
    }

}
