package pl.mkn.tdw.integrations.operationalcontext;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
final class OperationalContextValidationBaselineLoader implements OperationalContextValidationBaselineProvider {

    static final String BASELINE_RESOURCE = "classpath:operational-context-validation-baseline-v1.json";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private volatile OperationalContextValidationBaseline cachedBaseline;

    @Override
    public OperationalContextValidationBaseline load() {
        var baseline = cachedBaseline;
        if (baseline != null) {
            return baseline;
        }
        synchronized (this) {
            if (cachedBaseline == null) {
                cachedBaseline = readAndValidate();
            }
            return cachedBaseline;
        }
    }

    private OperationalContextValidationBaseline readAndValidate() {
        var resource = resourceLoader.getResource(BASELINE_RESOURCE);
        try (var inputStream = resource.getInputStream()) {
            var baseline = objectMapper.readValue(inputStream, OperationalContextValidationBaseline.class);
            if (baseline.schemaVersion() != 1
                    || !OperationalContextCatalogValidationService.FINGERPRINT_ALGORITHM.equals(
                    baseline.fingerprintAlgorithm())) {
                throw new IllegalStateException("Unsupported operational context validation baseline version");
            }
            var byFingerprint = new LinkedHashMap<String, OperationalContextValidationBaselineEntry>();
            for (var entry : baseline.findings()) {
                if (entry == null
                        || entry.fingerprint() == null
                        || !entry.fingerprint().matches("[a-f0-9]{64}")
                        || entry.ruleCode() == null
                        || entry.ruleCode().isBlank()
                        || OperationalContextCatalogValidationService.severityRank(entry.severity()) < 0) {
                    throw new IllegalStateException("Invalid operational context validation baseline entry");
                }
                if (byFingerprint.putIfAbsent(entry.fingerprint(), entry) != null) {
                    throw new IllegalStateException("Duplicate operational context validation baseline fingerprint");
                }
            }
            return new OperationalContextValidationBaseline(
                    baseline.schemaVersion(),
                    baseline.fingerprintAlgorithm(),
                    List.copyOf(byFingerprint.values())
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load operational context validation baseline", exception);
        }
    }
}

record OperationalContextValidationBaseline(
        int schemaVersion,
        String fingerprintAlgorithm,
        List<OperationalContextValidationBaselineEntry> findings
) {
    OperationalContextValidationBaseline {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    Map<String, OperationalContextValidationBaselineEntry> byFingerprint() {
        var result = new LinkedHashMap<String, OperationalContextValidationBaselineEntry>();
        findings.forEach(entry -> result.put(entry.fingerprint(), entry));
        return Map.copyOf(result);
    }
}

record OperationalContextValidationBaselineEntry(
        String fingerprint,
        String ruleCode,
        String severity
) {
}
