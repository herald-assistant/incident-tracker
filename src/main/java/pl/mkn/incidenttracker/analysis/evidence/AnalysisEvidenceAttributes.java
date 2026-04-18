package pl.mkn.incidenttracker.analysis.evidence;

import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnalysisEvidenceAttributes {

    private AnalysisEvidenceAttributes() {
    }

    public static Map<String, String> byName(List<AnalysisEvidenceAttribute> attributes) {
        var values = new LinkedHashMap<String, String>();

        for (var attribute : attributes) {
            if (!StringUtils.hasText(attribute.name())) {
                continue;
            }

            values.put(attribute.name(), attribute.value());
        }

        return Map.copyOf(values);
    }

    public static String text(Map<String, String> values, String key) {
        var value = values.get(key);
        return StringUtils.hasText(value) ? value : null;
    }

}
