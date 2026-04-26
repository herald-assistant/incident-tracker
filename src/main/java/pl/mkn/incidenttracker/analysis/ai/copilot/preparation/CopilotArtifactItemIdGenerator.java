package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

@Component
public class CopilotArtifactItemIdGenerator {

    public List<String> itemIds(AnalysisEvidenceSection section) {
        var itemCount = section != null && section.items() != null ? section.items().size() : 0;
        return IntStream.range(0, itemCount)
                .mapToObj(index -> itemId(section, index))
                .toList();
    }

    public String itemId(AnalysisEvidenceSection section, int zeroBasedIndex) {
        var prefix = "%s-%s".formatted(
                providerSlug(section != null ? section.provider() : null),
                slug(section != null ? section.category() : null)
        );
        return "%s-%03d".formatted(prefix, zeroBasedIndex + 1);
    }

    private String providerSlug(String provider) {
        var slug = slug(provider);
        if ("elasticsearch".equals(slug)) {
            return "elastic";
        }
        return slug;
    }

    private String slug(String value) {
        var normalized = value == null ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }
}
