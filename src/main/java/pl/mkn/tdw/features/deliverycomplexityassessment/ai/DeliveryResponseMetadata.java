package pl.mkn.tdw.features.deliverycomplexityassessment.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Diagnostic metadata is separate from the validated assessment or findings. */
final class DeliveryResponseMetadata {
    static final String QUALITY_FLAG = "AI_METADATA_WARNING";
    private final List<String> warnings = new ArrayList<>();

    double confidence(JsonNode node) {
        if (node != null && node.isNumber()) {
            var value = node.doubleValue();
            if (Double.isFinite(value) && value >= 0 && value <= 1) return value;
        }
        warn("confidence: brak poprawnej liczby 0-1; przyjęto 0 z powodu braku poprawnej deklaracji pewności.");
        return 0;
    }

    List<String> textList(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        if (node.isTextual()) {
            warn(field + ": oczekiwano listy tekstów; zachowano pojedynczy tekst.");
            return node.textValue().isBlank() ? List.of() : List.of(node.textValue());
        }
        if (!node.isArray()) {
            warn(field + ": niepoprawny typ; pominięto wyłącznie tę metadaną.");
            return List.of();
        }
        var values = new ArrayList<String>();
        var invalid = 0;
        for (var item : node) {
            if (item.isTextual() && !item.textValue().isBlank()) values.add(item.textValue());
            else invalid++;
        }
        if (invalid > 0) warn(field + ": pominięte niepoprawne elementy=" + invalid + "; zachowano poprawne teksty.");
        return List.copyOf(values);
    }

    void inspectCoverage(JsonNode node, List<String> expectedScope) {
        if (node == null || node.isMissingNode()) return; // The model is not asked to repeat the scope.
        if (!node.isArray()) {
            warn("coverage: niepoprawny typ deklaracji modelu; zakres pochodzi z materiału przekazanego przez aplikację. Oryginał zachowano w rawResponse.");
            return;
        }
        var declared = new LinkedHashSet<String>();
        var duplicates = 0;
        var invalid = 0;
        for (var item : node) {
            if (!item.isTextual() || item.textValue().isBlank()) invalid++;
            else if (!declared.add(item.textValue())) duplicates++;
        }
        var expected = new LinkedHashSet<>(expectedScope);
        var missing = expected.stream().filter(ref -> !declared.contains(ref)).count();
        var extra = declared.stream().filter(ref -> !expected.contains(ref)).count();
        if (missing > 0 || extra > 0 || duplicates > 0 || invalid > 0) {
            warn("coverage: brakujące=" + missing + ", nadmiarowe=" + extra + ", powtórzone=" + duplicates
                    + ", niepoprawne=" + invalid + ". Zakres pochodzi z materiału przekazanego przez aplikację; deklarację modelu zachowano w rawResponse. Referencje ustaleń zweryfikowano osobno.");
        }
    }

    void warn(String detail) {
        warnings.add("Metadane AI: " + detail);
    }

    List<String> withWarnings(List<String> values) {
        var combined = new LinkedHashSet<>(values);
        combined.addAll(warnings);
        return List.copyOf(combined);
    }

    List<String> qualityFlags(List<String> values) {
        if (warnings.isEmpty()) return values;
        var combined = new LinkedHashSet<>(values);
        combined.add(QUALITY_FLAG);
        return List.copyOf(combined);
    }
}
