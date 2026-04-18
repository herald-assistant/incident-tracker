package pl.mkn.incidenttracker.analysis.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import static pl.mkn.incidenttracker.analysis.operationalcontext.OperationalContextMaps.normalize;

record OperationalContextIncidentSignals(
        String corpus,
        Set<String> exactValues,
        Set<String> attributeNames
) {

    static OperationalContextIncidentSignals from(AnalysisContext context) {
        var corpusBuilder = new StringBuilder();
        var exactValues = new LinkedHashSet<String>();
        var attributeNames = new LinkedHashSet<String>();

        for (var section : context.evidenceSections()) {
            addText(corpusBuilder, section.provider());
            addText(corpusBuilder, section.category());

            for (var item : section.items()) {
                addText(corpusBuilder, item.title());
                addExactValue(exactValues, item.title());

                for (var attribute : item.attributes()) {
                    addText(corpusBuilder, attribute.name());
                    addText(corpusBuilder, attribute.value());
                    addExactValue(exactValues, attribute.value());
                    attributeNames.add(normalize(attribute.name()));
                }
            }
        }

        return new OperationalContextIncidentSignals(
                normalize(corpusBuilder.toString()),
                Set.copyOf(exactValues),
                Set.copyOf(attributeNames)
        );
    }

    boolean contains(String candidate) {
        var normalizedCandidate = normalize(candidate);
        return StringUtils.hasText(normalizedCandidate) && corpus.contains(normalizedCandidate);
    }

    boolean containsExact(String candidate) {
        var normalizedCandidate = normalize(candidate);
        return StringUtils.hasText(normalizedCandidate)
                && (exactValues.contains(normalizedCandidate) || contains(normalizedCandidate));
    }

    boolean containsAny(String... candidates) {
        for (var candidate : candidates) {
            if (contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    boolean matchesAttributeNames(String... names) {
        for (var name : names) {
            if (attributeNames.contains(normalize(name))) {
                return true;
            }
        }
        return false;
    }

    boolean isEmpty() {
        return exactValues.isEmpty() && !StringUtils.hasText(corpus);
    }

    private static void addText(StringBuilder builder, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value.toLowerCase(Locale.ROOT));
    }

    private static void addExactValue(Set<String> values, String value) {
        var normalizedValue = normalize(value);
        if (StringUtils.hasText(normalizedValue)) {
            values.add(normalizedValue);
        }
    }

}
